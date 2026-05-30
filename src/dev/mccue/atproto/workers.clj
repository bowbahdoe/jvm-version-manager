(ns dev.mccue.atproto.workers
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
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


(defn handle-commit
  [db payload]
  (let [{:keys [accessJwt]} (-> (http/post
                                  (str @service-endpoint create-session-xrpc)
                                  {:headers {"Content-Type" "application/json"}
                                   :body (json/generate-string {"identifier" (System/getenv "ATPROTO_INDEXER_HANDLE")
                                                                "password"   (System/getenv "ATPROTO_INDEXER_APP_PASSWORD")})})
                                (:body)
                                (json/parse-string-strict keyword))
        publisher-did       (get-in payload [:event :did])
        blob-link           (get-in payload [:event :commit :record :artifact :ref :$link])
        blob-response       (http/get
                              (str @service-endpoint get-blob-xrpc
                                   "?cid=" blob-link
                                   "&did=" publisher-did)
                              {:as :byte-array})
        module-info         (artifact/module-info-from-archive-bytes
                              (:body blob-response))]
    (jdbc/execute! db (sql/format
                        {:insert-into :repository.module_provider
                         :columns     [:atproto_did :module_name]
                         :values      [[publisher-did (:name module-info)]]
                         :on-conflict []
                         :do-nothing  true}))
    (let [providers (jdbc/execute! db (sql/format
                                       {:select [:atproto_did]
                                        :from   :repository.module_provider
                                        :where  [:= :module_name (:name module-info)]}))]
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
                   "Content-Type"  "application/json"}}))))

(comment
  (handle-commit nil nil))


(defn atproto_jetstream_event-processEvent
  [{:system/keys [db]} _job-type payload]
  (condp = (:kind (:event payload))
    "account" (do
                (log/debug "Account Event: cleaning up")
                (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                               :where [:= :id (UUID/fromString (:id payload))]})))
    "identity" (do
                 (log/debug "Identity Event: cleaning up")
                 (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                                :where [:= :id (UUID/fromString (:id payload))]})))
    "commit"   (handle-commit db payload)
    (log/info (str "Unhandled Event: " payload))))

(defn workers
  []
  {:atproto.jetstream_event/processEvent #'atproto_jetstream_event-processEvent})