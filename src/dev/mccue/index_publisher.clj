(ns dev.mccue.index-publisher
  (:require [camel-snake-kebab.core :as csk]
            [cheshire.core :as json]
            [chime.core]
            [clj-http.client :as http]
            [clojure.tools.logging :as log]
            [dev.mccue.atproto.diddy :as diddy]
            [dev.mccue.jsonquery :as jsonquery]
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


(defn- all-module-info-partial-query
  []
  [:name
   :cid
   :version
   :target_platform
   :synthetic
   :mandated
   :open
   [:exports
    {:select [:package
              :mandated
              :synthetic
              [:to {:select [:module]
                    :from :repository.module_exports_to
                    :join-on [:id :module_exports_id]}]]
     :from :repository.module_exports
     :join-on [:id :module_id]}]
   [:requires
    {:select [:module
              :version
              :static
              :transitive
              :mandated
              :synthetic]
     :from :repository.module_requires
     :join-on [:id :module_id]}]
   [:uses
    {:select [:service]
     :from :repository.module_uses
     :join-on [:id :module_id]}]
   [:provides
    {:select [:service :with]
     :from :repository.module_provides
     :join-on [:id :module_id]}]
   [:packages
    {:select [:package]
     :from :repository.module_package
     :join-on [:id :module_id]}]
   [:hashes {:select [:module
                      :algorithm
                      :hash]
             :from :repository.module_hash
             :join-on [:id :module_id]}]])

(comment)

(defn gen-index-info
  [db]
  (let [q (jsonquery/execute!
            db
            {:select [^:single [:module {:select (all-module-info-partial-query)
                                         :from :repository.module
                                         :join-on [:module_id :id]}]
                      :atproto_did]
             :from   :repository.published_module})
        modules (-> (for [[module-name info] (group-by (comp :name :module) q)]
                      {:module-name module-name
                       :providers   (for [[provider-did info]    (group-by :atproto_did info)]
                                      {:did provider-did
                                       :versions (for [[version modules] (group-by :version (map :module info))]
                                                   {:version version
                                                    :variants (for [module modules]
                                                                {:module {:cid (:cid module)
                                                                          :did provider-did}
                                                                 :moduleInfo (-> module
                                                                                 (dissoc :cid)
                                                                                 (update-keys csk/->camelCase)
                                                                                 (->> (filter (fn [[_ v]] (if (boolean? v) v (seq v)))))
                                                                                 (->> (into {})))})})})}))]
    modules))


(defn publish-index
  [db time]
  (log/info "About to publish updates to the index.")
  (let [modules              (gen-index-info db)
        {:keys [accessJwt
                refreshJwt]} (-> (http/post
                                   (str @service-endpoint create-session-xrpc)
                                   {:headers {"Content-Type" "application/json"}
                                    :body    (json/generate-string {"identifier" (System/getenv "ATPROTO_INDEXER_HANDLE")
                                                                    "password"   (System/getenv "ATPROTO_INDEXER_APP_PASSWORD")})})
                                 (:body)
                                 (json/parse-string-strict keyword))
        createdAt (str (OffsetDateTime/now))]
    (doseq [{:keys [module-name providers]} modules]
      (log/info "publishing index for" module-name)
      (http/post
        (str @service-endpoint put-record-xrpc)
        {:body    (json/generate-string
                    {:repo       @did
                     :rkey       module-name
                     :collection "dev.mccue.jvm.index"
                     :record     {"$type"     "dev.mccue.jvm.index"
                                  "createdAt" createdAt
                                  "providers" providers}})
         :headers {"Authorization" (str "Bearer " accessJwt)
                   "Content-Type"  "application/json"
                   "Accept"        "application/json"}})
      (log/info "Finished publishing index for" module-name)))


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
