(ns dev.mccue.atproto.workers
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.atproto.diddy :as diddy]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.repository.repository :as repository]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (com.fasterxml.uuid Generators)
           (java.time OffsetDateTime)
           (java.util UUID)
           (org.postgresql.util PGobject)))


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


(comment
  )


(defn handle-module-delete!
  [db payload]
  (log/info "Delete not yet handled" payload))

(defn handle-module-create!
  [db payload]
  (let [provider-did (get-in payload [:event :did])
        indexMe      (get-in payload [:event :commit :record :indexMe])]
    (if-not indexMe
      (log/info "indexMe is false. Skipping indexing module." provider-did)
      (let [rkey                  (get-in payload [:event :commit :rkey])
            createdAt             (get-in payload [:event :commit :record :createdAt])
            jetstream-module-id   (.generate (Generators/timeBasedEpochGenerator))
            jetstream-record      (get-in payload [:event :commit :record])]
        (jdbc/with-transaction [t db]
          (jdbc/execute! t (sql/format
                             {:insert-into :repository.jetstream_module
                              :columns [:id
                                        :record
                                        :provider_did
                                        :rkey
                                        :record_created_at]
                              :values [[jetstream-module-id
                                        (doto (PGobject.)
                                          (.setValue (json/generate-string jetstream-record))
                                          (.setType "jsonb"))
                                        provider-did
                                        rkey
                                        (OffsetDateTime/parse createdAt)]]}))

          (doall
            (for [variant (get-in jetstream-record [:variants])]
              (let [jetstream-module-variant-id (.generate (Generators/timeBasedEpochGenerator))]
                (jdbc/execute! t (sql/format {:insert-into :repository.jetstream_module_variant
                                              :columns     [:id
                                                            :jetstream_module_id
                                                            :artifact_cid
                                                            :license
                                                            :bill_of_materials
                                                            :cpu_architecture
                                                            :operating_system
                                                            :sourced_from_url
                                                            :sourced_from_cid
                                                            :sourced_from_aturi]
                                              :values [[jetstream-module-variant-id
                                                        jetstream-module-id
                                                        (get-in variant [:artifact :ref :$link])
                                                        (get-in variant [:license])
                                                        (get-in variant [:billOfMaterials])
                                                        (get-in variant [:cpuArchitecture])
                                                        (get-in variant [:operatingSystem])
                                                        (get-in variant [:sourcedFrom :url])
                                                        (get-in variant [:sourcedFrom :cid])
                                                        (get-in variant [:sourcedFrom :uri])]]}))))))))))



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
           (= (get-in payload [:event :commit :operation])
              "create"))
      (handle-module-create! db payload)

      (and (= event-kind "delete")
           (= (get-in payload [:event :commit :record :$type])
              "dev.mccue.jvm.module")
           (= (get-in payload [:event :commit :operation])
              "delete"))
      (handle-module-delete! db payload)

      :else
      (log/info (str "Unhandled Event: " payload)))))

(defn repository_jetstream_module_variant-checkModule
  [{:system/keys [db]} _job-type payload]
  (let [module-variant-info (jsonquery/execute-one! db
                                                    {:select [:id
                                                              :jetstream_module_id
                                                              :artifact_cid
                                                              :license
                                                              :bill_of_materials
                                                              :cpu_architecture
                                                              :operating_system
                                                              :sourced_from_url
                                                              :sourced_from_cid
                                                              :sourced_from_aturi
                                                              ^:single
                                                              [:jetstream-module
                                                               {:select [:id
                                                                         :record
                                                                         :provider_did
                                                                         :rkey
                                                                         :record_created_at]
                                                                :from :repository.jetstream_module
                                                                :join-on [:jetstream_module_id :id]}]]
                                                     :from :repository.jetstream_module_variant
                                                     :where [:= :id (parse-uuid (:id payload))]})
        blob-link     (get-in module-variant-info [:artifact_cid])
        publisher-did (get-in module-variant-info [:jetstream-module :provider_did])
        blob-response (http/get
                        (str @service-endpoint get-blob-xrpc
                             "?cid=" blob-link
                             "&did=" publisher-did)
                        {:as :byte-array})
        module-info (artifact/module-info-from-archive-bytes
                      (:body blob-response)
                      #_#_#_#_ :no-module-info-found-cb (constantly 0)
                      :more-than-one-module-info-found-cb (fn [entries]
                                                            (count entries)))

        module-name         (:name module-info)
        module-version      (:version module-info)
        rkey                (get-in module-variant-info [:jetstream-module :rkey])
        [rkey-module-name
         rkey-module-version] (string/split rkey #":")

        module-name-good      (if (not= rkey-module-name module-name)
                                (do (log/warn "Published module name does not match name in rkey. rkey="
                                              rkey
                                              ", module-name="
                                              module-name)
                                    false)
                                true)

        module-version-good (if (and rkey-module-version
                                     (not= module-version rkey-module-version))
                              (do (if module-version
                                    (log/warn "Published module version does not match version in rkey. rkey="
                                              rkey
                                              ", version in module-info=" module-version)
                                    (log/warn "Published module version does not match version in rkey. rkey="
                                              rkey
                                              ", version in module-info=null"))
                                  false)
                              true)]

    (jdbc/execute!
      db
      (sql/format
        {:update :repository.jetstream_module_variant
         :set {:rkey_module_version     rkey-module-version
               :artifact_module_version module-version
               :module_version_matches  module-version-good
               :rkey_module_name        rkey-module-name
               :artifact_module_name    module-name
               :module_name_matches     module-name-good
               :artifact_target_platform (:target-platform module-info)}
         :where [:= :id (parse-uuid (:id module-variant-info))]}))

    (when (and module-name-good
               module-version-good)

      ;    module_id                   uuid        not null references repository.module (id),
      ;    module_name                 text        not null,
      ;    module_version              text        not null,
      ;    jetstream_module_variant_id uuid        not null references repository.jetstream_module_variant (id)
      ;        on update restrict on delete restrict,
      (jdbc/execute! db (sql/format
                          {:insert-into :repository.published_module
                           :columns     [:module_name
                                         :module_version
                                         :jetstream_module_variant_id
                                         :module_info]
                           :values      [[module-name
                                          module-version
                                          (parse-uuid (:id module-variant-info))
                                          (json/generate-string module-info)]]
                           :on-conflict []
                           :do-nothing  true})))))

(defn workers
  []
  {:atproto.jetstream_event/processEvent #'atproto_jetstream_event-processEvent
   :repository.jetstream_module_variant/checkModule #'repository_jetstream_module_variant-checkModule})