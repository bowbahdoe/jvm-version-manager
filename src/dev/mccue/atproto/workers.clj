(ns dev.mccue.atproto.workers
  (:require [cheshire.core :as json]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.atproto.cid :as cid]
            [dev.mccue.atproto.diddy :as diddy]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.repository.module-info :as mi]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (com.fasterxml.uuid Generators)
           (java.time OffsetDateTime)
           (java.util UUID)
           (org.postgresql.util PGobject)))


(def get-blob-xrpc
  "/xrpc/com.atproto.sync.getBlob")



;;     did        text        not null,
;    rev        text        not null,
;    rkey       text        not null,
;    collection text        not null,
;    record     jsonb       not null,

;; https://atproto.com/specs/tid
;; "and the repo's rev must always increase -- if you see an event from a repo with an older rkey than the last one you saw from them, you can (and should) drop it."
;; TODO: handle :rev being a TID
(defn handle-record-create!
  [db payload]
  (log/info "Upserting ATProto record: did:" (get-in payload [:event :did])
            ", collection:" (get-in payload [:event :commit :collection])
            ", rkey:" (get-in payload [:event :commit :rkey]))
  (jdbc/execute! db
                 (sql/format
                   {:insert-into :atproto.record
                    :columns [:did :collection :rkey :rev :cid :record]
                    :values [[(get-in payload [:event :did])
                              (get-in payload [:event :commit :collection])
                              (get-in payload [:event :commit :rkey])
                              (get-in payload [:event :commit :rev])
                              (get-in payload [:event :commit :cid])
                              (doto (PGobject.)
                                (.setType "jsonb")
                                (.setValue
                                  (json/generate-string
                                    (get-in payload [:event :commit :record]))))]]
                    :on-conflict [:did :collection :rkey]
                    :do-update-set [:rev :record]})))

(defn handle-record-update!
  [db payload]
  (handle-record-create! db payload))


(defn handle-record-delete!
  [db payload]
  (log/info "Deleting ATProto record: did:" (get-in payload [:event :did])
            ", collection:" (get-in payload [:event :commit :collection])
            ", rkey:" (get-in payload [:event :commit :rkey]))
  (jdbc/execute! db
                 (sql/format
                   {:delete-from :atproto.record
                    :where [:and
                            [:= :did (get-in payload [:did])]
                            [:= :collection (get-in payload [:collection])]
                            [:= :rkey (get-in payload [:rkey])]]})))

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
           (= (get-in payload [:event :commit :operation])
              "create"))
      (handle-record-create! db payload)

      (and (= event-kind "commit")
           (= (get-in payload [:event :commit :operation])
              "update"))
      (handle-record-update! db payload)

      (and (= event-kind "commit")
           (= (get-in payload [:event :commit :operation])
              "delete"))
      (handle-record-delete! db payload)

      :else
      (log/info (str "Unhandled Event: " payload)))))

(defn atproto_record-processModule
  [{:system/keys [db]} _job-type payload]
  (if (not= (get-in payload [:record :$type]) "dev.mccue.jvm.module")
    (log/info "Record is not a dev.mccue.jvm.module. Skipping.")

    (do
      (log/info "Creating module records." (:rkey payload))
      (jdbc/with-transaction
        [t db]
        (let [{:keys [record]} payload]
          (jdbc/execute-one!
            t
            (sql/format
              {:insert-into :atproto.dev_mccue_jvm_module
               :columns [:atproto_record_id
                         :record_created_at]
               :values [[(parse-uuid (:id payload))
                         (OffsetDateTime/parse (:createdAt record))]]
               :on-conflict [:atproto_record_id]
               :do-update-set [:record_created_at]}))
          (let [{:dev_mccue_jvm_module/keys [id]} (jdbc/execute-one!
                                                    t
                                                    (sql/format
                                                      {:select [:id]
                                                       :from :atproto.dev_mccue_jvm_module
                                                       :where [:= :atproto_record_id (parse-uuid (:id payload))]}))]

            (jdbc/execute-one! t (sql/format
                                   {:delete-from :atproto.dev_mccue_jvm_module_variant
                                    :where [:= :dev_mccue_jvm_module_id id]}))
            (doseq [variant (:variants record)]
              (let [variant-id (.generate (Generators/timeBasedEpochGenerator))]
                (jdbc/execute-one!
                  t
                  (sql/format {:insert-into :atproto.dev_mccue_jvm_module_variant
                               :columns     [:id
                                             :dev_mccue_jvm_module_id
                                             :license
                                             :sourced_from_url
                                             :sourced_from_cid
                                             :sourced_from_aturi
                                             :artifact_cid_link
                                             :artifact_size]
                               :values      [[variant-id
                                              id
                                              (:license variant)
                                              (get-in variant [:sourcedFrom :url])
                                              (get-in variant [:sourcedFrom :cid])
                                              (get-in variant [:sourcedFrom :uri])
                                              (get-in variant [:artifact :ref :$link])
                                              (get-in variant [:artifact :size])]]}))
                (when (seq (:attributes variant))
                  (jdbc/execute! t
                                 (sql/format {:insert-into :atproto.dev_mccue_jvm_module_variant_attribute
                                              :columns [:dev_mccue_jvm_module_variant_id
                                                        :name
                                                        :value]
                                              :values (vec (for [{:keys [name value]} (:attributes variant)]
                                                             [variant-id name value]))})))))))))))

(defn persist-module!
  [db variant-id cid module-info]
  (jdbc/with-transaction
    [t db]
    (let [rs (jdbc/execute-one! t (sql/format
                                    {:insert-into :repository.module
                                     :columns     [:name
                                                   :version
                                                   :target_platform
                                                   :mandated
                                                   :synthetic
                                                   :open
                                                   :cid]
                                     :values      [[(:name module-info)
                                                    (:version module-info)
                                                    (:target-platform module-info)
                                                    (or (:mandated module-info) false)
                                                    (or (:synthetic module-info) false)
                                                    (or (:open module-info) false)
                                                    cid]]
                                     :on-conflict [:cid]
                                     :do-nothing true
                                     :returning   [:id]}))
          module-id (:module/id rs)]
      ;; If we don't get a module id, we shouldn't need to derive more info
      (when module-id
        (doseq [provides (:provides module-info)]
          (doseq [with (:with provides)]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_provides(
                                 module_id,
                                 service,
                                 \"with\"
                              )
                              VALUES (?, ?, ?)
                              ON CONFLICT DO NOTHING")
                              module-id
                              (:service provides)
                              with])))

        (doseq [uses (:uses module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_uses(
                                   module_id,
                                   service
                                )
                                VALUES (?, ?)
                                ON CONFLICT DO NOTHING")
                            module-id
                            (:service uses)]))
        (doseq [requires (:requires module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_requires(
                                 module_id,
                                 module,
                                 version,
                                 static,
                                 transitive,
                                 mandated,
                                 synthetic
                              )
                              VALUES (?, ?, ?, ?, ?, ?, ?)
                              ON CONFLICT DO NOTHING")
                            module-id
                            (:module requires)
                            (:version requires)
                            (or (:static requires)
                                false)
                            (or (:transitive requires)
                                false)
                            (or (:mandated requires)
                                false)
                            (or (:synthetic requires)
                                false)]))
        (doseq [exports (:exports module-info)]
          (let [exports-id (.generate (Generators/timeBasedEpochGenerator))]
            (jdbc/execute! t (sql/format
                               {:insert-into :repository.module_exports
                                :columns [:id :module_id :package :mandated :synthetic]
                                :values [[exports-id
                                          module-id
                                          (:package exports)
                                          (or (:mandated exports)
                                              false)
                                          (or (:synthetic exports)
                                              false)]]}))

            (doseq [to (:to exports)]
              (jdbc/execute! t (sql/format
                                 {:insert-into :repository.module_exports_to
                                  :columns [:module_exports_id :module]
                                  :values [[exports-id to]]})))))

        (doseq [{:keys [package]} (:packages module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_package(
                                   module_id,
                                   package
                                )
                                VALUES (?, ?)
                                ON CONFLICT DO NOTHING")
                            module-id
                            package]))

        (let [{:keys [algorithm hashes]} (:hashes module-info)]
          (doseq [{:keys [module hash]} hashes]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_hash(
                                     module_id,
                                     module,
                                     algorithm,
                                     hash
                                  )
                                  VALUES (?, ?, ?, ?)
                                  ON CONFLICT DO NOTHING")
                              module-id
                              module
                              algorithm
                              hash])))))))

(defn auto-publish-module!
  [db publisher-did artifact-cid-link attributes]
  (let [published-module-id (.generate (Generators/timeBasedEpochGenerator))]
    (jdbc/with-transaction [t db]
      (jdbc/execute! t (sql/format {:insert-into [:repository.published_module]
                                    :columns [:id :atproto_did :module_id]
                                    :values [[published-module-id publisher-did {:select [:id]
                                                                                 :from :repository.module
                                                                                 :where [:= :cid artifact-cid-link]}]]}))
      (when (seq attributes)
        (jdbc/execute! t (sql/format {:insert-into :repository.published_module_attribute
                                      :columns [:published_module_id :name :value]
                                      :values  (vec (for [attribute attributes]
                                                      [published-module-id (:name attribute) (:value attribute)]))}))))))

(defn atproto_dev_mccue_jvm_module-importModule
  [{:system/keys [db]} _job-type payload]
  (let [atproto-record-info (jsonquery/execute-one!
                              db
                              {:select [:atproto_record_id
                                        ^:single
                                        [:atproto_record {:select [:did :collection :rkey :rev :cid]
                                                          :from :atproto.record
                                                          :join-on [:atproto_record_id :id]}]
                                        [:variants {:select [:id
                                                             :license
                                                             :sourced_from_url
                                                             :sourced_from_cid
                                                             :sourced_from_aturi
                                                             :artifact_cid_link
                                                             :artifact_size
                                                             [:attributes {:select [:name :value]
                                                                           :from :atproto.dev_mccue_jvm_module_variant_attribute
                                                                           :join-on [:id :dev_mccue_jvm_module_variant_id]}]]
                                                    :from :atproto.dev_mccue_jvm_module_variant
                                                    :join-on [:id :dev_mccue_jvm_module_id]}]]
                               :from :atproto.dev_mccue_jvm_module
                               :where [:= :id (parse-uuid (:id payload))]})]
    (let [rkey                  (:rkey (:atproto_record atproto-record-info))
          [rkey-module-name
           rkey-module-version] (string/split rkey #":")]
      (doseq [variant (:variants atproto-record-info)]
        (let [variant-id (parse-uuid (:id variant))
              log-and-persist! (fn [error-message]
                                 (log/error error-message)
                                 (jdbc/execute! db (sql/format
                                                     {:insert-into :atproto.dev_mccue_jvm_module_variant_error
                                                      :columns     [:dev_mccue_jvm_module_variant_id :error]
                                                      :values      [[variant-id error-message]]})))]
          (try
            (let [{:keys [artifact_cid_link
                          artifact_size]} variant
                  publisher-did               (:did (:atproto_record atproto-record-info))
                  _                           (log/info "Fetching artifact for variant of"
                                                        rkey
                                                        variant)
                  service-endpoint            (diddy/resolve-service-endpoint
                                                (diddy/resolve-did-document publisher-did))
                  fetched-artifact            (artifact/fetch-artifact-cached
                                                db
                                                {:url (str service-endpoint get-blob-xrpc
                                                           "?cid=" artifact_cid_link
                                                           "&did=" publisher-did)})
                  artifact-bytes (:bytes fetched-artifact)
                  computed-cid (cid/sha256-bytes->cid-string
                                 (cid/bytes->sha256-bytes (:bytes fetched-artifact)))]
              (if (not= artifact_size (alength artifact-bytes))
                (log-and-persist! (str "Artifact size and expected artifact size are different. actual="
                                       (alength artifact-bytes)
                                       ", expected="
                                       artifact_size))

                (if (not= artifact_cid_link computed-cid)
                  (log-and-persist! (str "Artifact CID and CID computed from bytes are different. artifact_cid="
                                         artifact_cid_link
                                         ", computed_cid="
                                         computed-cid))
                  ;; We only proceed to look for a module info if hashes match
                  (let [module-info-entries (artifact/module-info-entries-from-archive artifact-bytes)]
                    (cond
                      (= (count module-info-entries) 0)
                      (log-and-persist! (str "More than one module-info.class found in archive. "
                                             (string/join ", " (map :name module-info-entries))))
                      (> (count module-info-entries) 1)
                      (log-and-persist! (str "More than one module-info.class found in archive. "
                                             (string/join ", " (map :name module-info-entries))))

                      :else
                      (let [module-info-entry (first module-info-entries)
                            module-info (mi/from-bytes (:bytes module-info-entry))
                            module-name-matches (= rkey-module-name (:name module-info))
                            module-version-matches (= rkey-module-version (:version module-info))]
                        (when-not module-name-matches
                          (log-and-persist!
                            (str "Module name does not match. expected: "
                                 rkey-module-name
                                 " based on the record. found: "
                                 (:name module-info))))
                        (when-not module-version-matches
                          (log-and-persist!
                            (str "Module version does not match. expected: "
                                 (or rkey-module-version "<no version>")
                                 " based on the record. found: "
                                 (or (:version module-info) "<no version>"))))
                        (persist-module! db variant-id artifact_cid_link module-info)
                        (auto-publish-module! db publisher-did artifact_cid_link (:attributes variant))))))))

            (catch Exception e
              (log-and-persist! (Exception/.getMessage e)))))))))

(defn workers
  []
  {:atproto.jetstream_event/processEvent      #'atproto_jetstream_event-processEvent
   :atproto.record/processModule              #'atproto_record-processModule
   :atproto.dev_mccue_jvm_module/importModule #'atproto_dev_mccue_jvm_module-importModule})