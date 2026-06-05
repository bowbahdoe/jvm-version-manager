(ns dev.mccue.repository.repository
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.repository.artifact :refer [fetch-artifact]]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (com.fasterxml.uuid Generators)
           (java.io InputStream)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.security MessageDigest)
           (java.util HexFormat)
           (org.sqlite SQLiteDataSource)))




(defn persist-module
  [db artifact]
  (let [cid (artifact/persist-artifact db artifact)]
    (jdbc/with-transaction
      [t db]
      (let [rs (jdbc/execute-one! t [(String/.stripIndent
                                       "INSERT INTO repository.module(
                                         name,
                                         version,
                                         target_platform,
                                         mandated,
                                         synthetic,
                                         module_info,
                                         mvn_repository,
                                         mvn_groupId,
                                         mvn_artifactId,
                                         mvn_version,
                                         mvn_classifier,
                                         type,
                                         cid,
                                         user_id
                                       )
                                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                       ON CONFLICT DO NOTHING
                                       RETURNING id")
                                     (:name (:module-info artifact))
                                     (:version (:module-info artifact))
                                     (or (:target-platform (:module-info artifact))
                                         "universal")
                                     (or (:mandated (:module-info artifact))
                                         false)
                                     (or (:synthetic (:module-info artifact))
                                         false)
                                     (with-out-str
                                       (pprint/pprint (:module-info artifact)))
                                     (:mvn/repository artifact)
                                     (:mvn/groupId artifact)
                                     (:mvn/artifactId artifact)
                                     (:mvn/version artifact)
                                     (:mvn/classifier artifact)
                                     (name (:type artifact))
                                     cid
                                     (:user_id artifact)])
            module-id (:module/id rs)]
        ;; On initial insert we get the id back,
        ;; so that's our cue to insert the rest of the info.
        (when module-id
          (doseq [provides (:provides (:module-info artifact))]
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

          (doseq [uses (:uses (:module-info artifact))]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_uses(
                                     module_id,
                                     service
                                  )
                                  VALUES (?, ?)
                                  ON CONFLICT DO NOTHING")
                              module-id
                              (:service uses)]))
          (doseq [requires (:requires (:module-info artifact))]
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
          (doseq [exports (:exports (:module-info artifact))]
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
                                    :values [[exports-id
                                              to]]})))))

          (doseq [{:keys [package]} (:packages (:module-info artifact))]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_package(
                                     module_id,
                                     package
                                  )
                                  VALUES (?, ?)
                                  ON CONFLICT DO NOTHING")
                              module-id
                              package]))

          (let [{:keys [algorithm hashes]} (:hashes (:module-info artifact))]
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
                                hash]))))))))
