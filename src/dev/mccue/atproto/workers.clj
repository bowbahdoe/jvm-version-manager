(ns dev.mccue.atproto.workers
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.atproto.diddy :as diddy]
            [dev.mccue.repository.artifact :as artifact]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.time OffsetDateTime)
           (java.util UUID)))


(def create-session-xrpc
  "/xrpc/com.atproto.server.createSession")

(def put-record-xrpc
  "/xrpc/com.atproto.repo.putRecord")

(def get-blob-xrpc
  "/xrpc/com.atproto.sync.getBlob")

(def did
  (delay
    (diddy/get-did
      (System/getenv "ATPROTO_INDEXER_HANDLE"))))

(def service-endpoint
  (delay
    (diddy/resolve-service-endpoint
      (diddy/resolve-did-document @did))))


(defn handle-module-create!
  [db payload]
  (let [publisher-did (get-in payload [:event :did])]
    (if-not (get-in payload [:event :commit :record :indexMe])
      (log/info "indexMe is false. Skipping indexing module." publisher-did)
      (let [blob-link           (get-in payload [:event :commit :record :artifact :ref :$link])
            blob-response       (http/get
                                  (str @service-endpoint get-blob-xrpc
                                       "?cid=" blob-link
                                       "&did=" publisher-did)
                                  {:as :byte-array})
            module-info         (artifact/module-info-from-archive-bytes
                                  (:body blob-response))
            module-name         (:name module-info)
            module-version      (:version module-info)
            rkey                (get-in payload [:event :commit :rkey])
            [rkey-module-name
             rkey-module-version] (string/split rkey #":")]
        (cond
          (not= rkey-module-name module-name)
          (log/warn "Published module name does not match name in rkey. rkey="
                    rkey
                    ", module-name="
                    module-name)

          (and rkey-module-version
               (not= module-version rkey-module-version))
          (if module-version
            (log/warn "Published module version does not match version in rkey. rkey="
                      rkey
                      ", version in module-info=" module-version)
            (log/warn "Published module version does not match version in rkey. rkey="
                      rkey
                      ", version in module-info=null"))



          :else
          (do

            (jdbc/execute! db (sql/format
                                {:insert-into :repository.module_provider
                                 :columns     [:atproto_did :module_name]
                                 :values      [[publisher-did (:name module-info)]]
                                 :on-conflict []
                                 :do-nothing  true}))
            (let [providers          (jdbc/execute! db (sql/format
                                                        {:select [:atproto_did]
                                                         :from   :repository.module_provider
                                                         :where  [:= :module_name (:name module-info)]}))
                  {:keys [accessJwt]} (-> (http/post
                                            (str @service-endpoint create-session-xrpc)
                                            {:headers {"Content-Type" "application/json"}
                                             :body (json/generate-string {"identifier" (System/getenv "ATPROTO_INDEXER_HANDLE")
                                                                          "password"   (System/getenv "ATPROTO_INDEXER_APP_PASSWORD")})})
                                          (:body)
                                          (json/parse-string-strict keyword))]
              (http/post
                (str @service-endpoint put-record-xrpc)
                {:body (json/generate-string
                         {:repo       @did
                          :rkey       (:name module-info)
                          :collection "dev.mccue.jvm.index"
                          :record     {"$type" "dev.mccue.jvm.index"
                                       "createdAt" (str (OffsetDateTime/now))
                                       "providers" (for [provider providers]
                                                     {"did" (:module_provider/atproto_did provider)})}})
                 :headers {"Authorization" (str "Bearer " accessJwt)
                           "Content-Type"  "application/json"}}))))))))

(comment
  (handle-module-create! nil nil))


(defn atproto_jetstream_event-processEvent
  [{:system/keys [db]} _job-type payload]
  (let [event-kind (:kind (:event payload))]
    (cond
      (= event-kind "account")
      (do
        (log/debug "Account Event: cleaning up")
        (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                       :where [:= :id (UUID/fromString (:id payload))]})))
      (= event-kind "identity")
      (do
        (log/debug "Identity Event: cleaning up")
        (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                       :where [:= :id (UUID/fromString (:id payload))]})))
      (and (= event-kind "commit")
           (= (get-in payload [:event :commit :record :$type])
              "dev.mccue.jvm.module")
           (or (= (get-in payload [:event :commit :operation])
                  "create")
               ;; TODO: updates and deletes very likely should have different handling
               (= (get-in payload [:event :commit :operation])
                  "update")))
      (handle-module-create! db payload)

      :else
      (log/info (str "Unhandled Event: " payload)))))

(defn workers
  []
  {:atproto.jetstream_event/processEvent #'atproto_jetstream_event-processEvent})