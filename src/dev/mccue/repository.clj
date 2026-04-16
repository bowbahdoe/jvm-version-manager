(ns dev.mccue.repository
  (:require [clojure.string :as string]
            [next.jdbc :as jdbc]
            [clojure.pprint :as pprint]
            [dev.mccue.jmod :refer [fetch-artifact determine-archive-type]])
  (:import (java.security MessageDigest)
           (java.util HexFormat)
           (org.sqlite SQLiteDataSource)))

(defn from-file
  [path]
  (let [db (doto (SQLiteDataSource.)
             (SQLiteDataSource/.setUrl (str "jdbc:sqlite:" path)))]
    (doseq [command (-> (slurp "init.sql")
                        (string/split #"--;"))]
      (jdbc/execute! db [command]))

    db))

(defn persist-artifact
  [db artifact]
  (let [digest (MessageDigest/getInstance "sha256")
        hash (HexFormat/.formatHex (HexFormat/of)
                                   (MessageDigest/.digest digest (:bytes artifact)))]
    (jdbc/execute! db [(String/.stripIndent
                         "INSERT INTO artifact(sha256, data)
                          VALUES (?, ?)
                          ON CONFLICT DO NOTHING")
                       hash
                       (:bytes artifact)])
    hash))

(defn persist-module
  [db artifact]
  (let [sha256 (persist-artifact db artifact)]
    (jdbc/with-transaction
      [t db]
      (let [rs (jdbc/execute-one! t [(String/.stripIndent
                                       "INSERT INTO module(
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
                                         sha256
                                       )
                                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                       ON CONFLICT DO NOTHING
                                       RETURNING id")
                                     (:name artifact)
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
                                     sha256])
            module-id (:module/id rs)]
        ;; On initial insert we get the id back,
        ;; so that's our cue to insert the rest of the info.
        (when module-id
          (doseq [provides (:provides (:module-info artifact))]
            (doseq [with (:with provides)]
              (jdbc/execute! t [(String/.stripIndent
                                  "INSERT INTO module_provides(
                                   module_id,
                                   service,
                                   with
                                )
                                VALUES (?, ?, ?)
                                ON CONFLICT DO NOTHING")
                                module-id
                                (:service provides)
                                with])))

          (doseq [uses (:uses (:module-info artifact))]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO module_uses(
                                     module_id,
                                     service
                                  )
                                  VALUES (?, ?)
                                  ON CONFLICT DO NOTHING")
                              module-id
                              (:service uses)]))
          (doseq [requires (:requires (:module-info artifact))]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO module_requires(
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
                                  "INSERT INTO module_exports(
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
                                    false)]))))))))



(defn retrieve-artifact
  [db sha256]
  (:artifact/data
    (jdbc/execute-one! db [(String/.stripIndent
                             "SELECT sha256, data
                              FROM artifact
                              WHERE sha256 = ?")
                           sha256])))


(defn fetch-cached
  [db artifact]
  (let [{:keys [hashes]} artifact
        {:strs [sha256]} hashes]
    (or (and sha256 (some->> (retrieve-artifact db sha256)
                             (assoc {:cached true} :bytes)))
        (let [procured (fetch-artifact artifact)]
          (persist-artifact db procured)
          procured))))
(comment
  (require '[dev.mccue.jmod])
  (require '[dev.mccue.descriptors])
  (from-file "modules.db")
  (persist-artifact (from-file "modules.db")
                    (first
                      (dev.mccue.jmod/procure (dev.mccue.descriptors/dev-mccue-jdbc))))
  (retrieve-artifact (from-file "modules.db")
                     "0da876dba16e9ade6c3ec5448e1b589b7332d007cc89b75309cd10674112380d"))


(defn build-distro
  []
  (let [db (from-file "modules.db")
        platform "windows-amd64"]
    (loop [modules {}]
      (let [results (jdbc/execute! db [(String/.stripIndent
                                         "SELECT name, version, target_platform
                                          FROM module
                                          WHERE name='java.base' AND (target_platform = 'universal' OR target_platform = ?)")
                                       platform])]
        (println "Pick your java.base:")
        (doseq [{:module/keys [name version target_platform]} results]
          (println (str "1: " name "@" version ", " target_platform)))))))