(ns dev.mccue.index-publisher
  (:require [cheshire.core :as json]
            [chime.core]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [dev.mccue.atproto.diddy :as diddy]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.lang AutoCloseable)
           (java.time Duration Instant OffsetDateTime)))

(def create-session-xrpc
  "/xrpc/com.atproto.server.createSession")

(def refresh-session-xrpc
  "/xrpc/com.atproto.server.refreshSession")

(def put-record-xrpc
  "/xrpc/com.atproto.repo.putRecord")

(def get-blob-xrpc
  "/xrpc/com.atproto.sync.getBlob")

(def handle (System/getenv "ATPROTO_INDEXER_HANDLE"))

(def did
  (delay (diddy/get-did handle)))

(def service-endpoint
  (delay
    (diddy/resolve-service-endpoint
      (diddy/resolve-did-document @did))))


(defn publish-index
  [db time]
  (log/info "About to publish updates to the index.")
  (let [published-modules (jdbc/execute! db ["SELECT
                                                   DISTINCT repository.published_module.module_name,
                                                            repository.jetstream_module.provider_did
                                                FROM repository.published_module
                                                LEFT JOIN repository.jetstream_module_variant
                                                  ON repository.published_module.jetstream_module_variant_id = repository.jetstream_module_variant.id
                                                LEFT JOIN repository.jetstream_module
                                                  ON repository.jetstream_module_variant.jetstream_module_id = repository.jetstream_module.id"])
        name->dids         (-> (group-by :published_module/module_name published-modules)
                               (update-vals #(sort (set (map :jetstream_module/provider_did %)))))
        {:keys [accessJwt
                refreshJwt]} (-> (http/post
                                   (str @service-endpoint create-session-xrpc)
                                   {:headers {"Content-Type" "application/json"}
                                    :body (json/generate-string {"identifier" (System/getenv "ATPROTO_INDEXER_HANDLE")
                                                                 "password"   (System/getenv "ATPROTO_INDEXER_APP_PASSWORD")})})
                                 (:body)
                                 (json/parse-string-strict keyword))
        createdAt (str (OffsetDateTime/now))]
       (println name->dids)
       (doseq [[name dids] name->dids]
         (log/info "publishing index for" name)
         (http/post
           (str @service-endpoint put-record-xrpc)
           {:body (json/generate-string
                    {:repo       @did
                     :rkey       name
                     :collection "dev.mccue.jvm.index"
                     :record     {"$type" "dev.mccue.jvm.index"
                                  "createdAt" createdAt
                                  "providers" (for [did dids]
                                                {"did" did})}})
            :headers {"Authorization" (str "Bearer " accessJwt)
                      "Content-Type"  "application/json"
                      "Accept"  "application/json"}})
         (log/info "Finished publishing index for" name)))


  #_(let [providers (jdbc/execute! db (sql/format
                                        {:select [:atproto_did]
                                         :from   :repository.module_provider
                                         :where  [:= :module_name (:name module-info)]}))
          ;; TODO: need to use the refresh token
          {:keys [accessJwt
                  refreshJwt]} (-> (http/post
                                     (str @service-endpoint create-session-xrpc)
                                     {:headers {"Content-Type" "application/json"}
                                      :body    (json/generate-string {"identifier" (System/getenv "ATPROTO_INDEXER_HANDLE")
                                                                      "password"   (System/getenv "ATPROTO_INDEXER_APP_PASSWORD")})})
                                   (:body)
                                   (json/parse-string-strict keyword))]
      (http/post
        (str @service-endpoint put-record-xrpc)
        {:body    (json/generate-string
                    {:repo       @did
                     :rkey       (:name module-info)
                     :collection "dev.mccue.jvm.index"
                     :record     {"$type"     "dev.mccue.jvm.index"
                                  "createdAt" (str (OffsetDateTime/now))
                                  "providers" (for [provider providers]
                                                {"did" (:module_provider/atproto_did provider)})}})
         :headers {"Authorization" (str "Bearer " accessJwt)
                   "Content-Type"  "application/json"}})))


(defn start-index-publisher!
  [{:system/keys [db]}]
  (chime.core/chime-at
    (chime.core/periodic-seq (Instant/now) (Duration/ofHours 1))
    (partial #'publish-index db)))

(defn stop-index-publisher!
  [index-publisher]
  (AutoCloseable/.close index-publisher))
