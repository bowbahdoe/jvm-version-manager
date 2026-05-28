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
  (:import (java.io InputStream)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.security MessageDigest)
           (java.util HexFormat)
           (org.sqlite SQLiteDataSource)))




(defn persist-module
  [db artifact]
  (let [sha256 (artifact/persist-artifact db artifact)]
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
                                         sha256,
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
                                     sha256
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
            (doseq [to (if (seq (:to exports))
                         (:to exports)
                         [nil])]
              (jdbc/execute! t [(String/.stripIndent
                                  "INSERT INTO repository.module_exports(
                                     module_id,
                                     package,
                                     \"to\",
                                     mandated,
                                     synthetic
                                  )
                                  VALUES (?, ?, ?, ?, ?)
                                  ON CONFLICT DO NOTHING")
                                module-id
                                (:package exports)
                                to
                                (or (:mandated exports)
                                    false)
                                (or (:synthetic exports)
                                    false)])))

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





(defn- sqlite-db
  [path]
  (let [db (doto (SQLiteDataSource.)
             (SQLiteDataSource/.setUrl (str "jdbc:sqlite:" path)))]
    (doseq [command (-> (slurp (io/resource "sqlite-init.sql"))
                        (string/split #"--;"))]
      (jdbc/execute! db [command]))
    db))

(defn get-matching-columns
  [postgres-db sqlite-db table]
  (let [postgres-columns (mapv :columns/column_name
                               (jdbc/execute! postgres-db
                                              (sql/format
                                                {:select [:column_name]
                                                 :from :information_schema.columns
                                                 :where [:and
                                                         [:= :table_schema "repository"]
                                                         [:= :table_name (name table)]]})))
        sqlite-columns   (mapv :name
                               (jdbc/execute! sqlite-db
                                              [(str "pragma table_info(" (name table) ")")]))]

    (mapv keyword
          (sort
            (set/intersection (set (map string/lower-case postgres-columns))
                              (set (map string/lower-case sqlite-columns)))))))


(defn build-index
  [db]
  (let [temp-file (Files/createTempFile "new" ".db" (into-array FileAttribute []))]
    (try
      (jdbc/with-transaction [t db]
        (let [index  (str temp-file)
              new-db (sqlite-db index)

              transfer! (fn [table]
                          (log/info (str "Transferring " table))
                          (let [columns (get-matching-columns t new-db table)
                                source (jdbc/execute! t
                                                      (sql/format
                                                        {:select (mapv #(keyword (name table) (name %))
                                                                       columns)
                                                         :from (keyword (str "repository." (name table)))}
                                                        {:quoted true}))]

                            (when (seq source)
                              (jdbc/execute! new-db (sql/format
                                                      {:insert-into table
                                                       :columns columns
                                                       :values (mapv (apply juxt (mapv #(keyword (name table) (name %))
                                                                                       columns))
                                                                     source)}
                                                      {:quoted true})))))]

          (transfer! :module)
          (transfer! :module_provides)
          (transfer! :module_uses)
          (transfer! :module_requires)
          (transfer! :module_exports)
          (transfer! :module_package)
          (transfer! :module_hash)

          (with-open [is (io/input-stream (Path/.toFile temp-file))]
            (InputStream/.readAllBytes is))))
      (finally (Files/deleteIfExists temp-file)))))

(comment
  (build-index (user/db)))