(ns dev.mccue.ingestion
  (:require [honey.sql :as h]
            [next.jdbc :as jdbc]
            [dev.mccue.jmod :as jmod]
            [dev.mccue.repository :as repository]
            [next.jdbc.transaction])
  (:import (java.time OffsetDateTime)))

(defn fetch-jdk
  [{:system/keys [db]}]
  (println "Fetching next JDK")
  (try
    (binding [next.jdbc.transaction/*nested-tx* :ignore]
      (if-let [job (jdbc/execute-one! db (h/format {:select [:jdk_ingestion_job/id
                                                             :jdk_ingestion_job/windows_amd64_url
                                                             :jdk_ingestion_job/windows_amd64_sha256_url
                                                             :jdk_ingestion_job/windows_amd64_sha256
                                                             :jdk_ingestion_job/macos_aarch64_url
                                                             :jdk_ingestion_job/macos_aarch64_sha256_url
                                                             :jdk_ingestion_job/macos_aarch64_sha256
                                                             :jdk_ingestion_job/linux_aarch64_url
                                                             :jdk_ingestion_job/linux_aarch64_sha256_url
                                                             :jdk_ingestion_job/linux_aarch64_sha256
                                                             :jdk_ingestion_job/linux_amd64_url
                                                             :jdk_ingestion_job/linux_amd64_sha256_url
                                                             :jdk_ingestion_job/linux_amd64_sha256
                                                             :jdk_ingestion_job/provider_id]
                                                    :from :jdk_ingestion_job
                                                    :where [:= :jdk_ingestion_job/finished_at nil]
                                                    :limit 1}))]
        (try
          (jdbc/execute! db (h/format {:update :jdk_ingestion_job
                                       :set {:jdk_ingestion_job/started_at [(str (OffsetDateTime/now))]}
                                       :where  [:= :jdk_ingestion_job/id (:jdk_ingestion_job/id job)]}))

          (let [{:jdk_ingestion_job/keys [windows_amd64_url
                                          windows_amd64_sha256_url
                                          windows_amd64_sha256
                                          macos_aarch64_url
                                          macos_aarch64_sha256_url
                                          macos_aarch64_sha256
                                          linux_aarch64_url
                                          linux_aarch64_sha256_url
                                          linux_aarch64_sha256
                                          linux_amd64_url
                                          linux_amd64_sha256_url
                                          linux_amd64_sha256]} job
                artifacts (jmod/procure {:fetch (partial #'repository/fetch-cached db)}
                                        (doto
                                          {:type :jdk
                                           :artifacts (cond-> []
                                                              windows_amd64_url
                                                              (conj {:url windows_amd64_url
                                                                     :hashes {"sha256" (or windows_amd64_sha256
                                                                                           (slurp windows_amd64_sha256_url))}})

                                                              macos_aarch64_url
                                                              (conj {:url macos_aarch64_url
                                                                     :hashes {"sha256" (or macos_aarch64_sha256
                                                                                           (slurp macos_aarch64_sha256_url))}})

                                                              linux_aarch64_url
                                                              (conj {:url linux_aarch64_url
                                                                     :hashes {"sha256" (or linux_aarch64_sha256
                                                                                           (slurp linux_aarch64_sha256_url))}})

                                                              linux_amd64_url
                                                              (conj {:url linux_amd64_url
                                                                     :hashes {"sha256" (or linux_amd64_sha256
                                                                                           (slurp linux_amd64_sha256_url))}}))}

                                          (println)))]
            (binding [next.jdbc.transaction/*nested-tx* :ignore]
              (doseq [artifact artifacts]
                (repository/persist-module db artifact))
              (jmod/create-jdk-module-sets db)))

          (catch Exception e
            (jdbc/execute! db (h/format {:update :jdk_ingestion_job
                                         :set {:jdk_ingestion_job/error (.getMessage e)}
                                         :where [:= :jdk_ingestion_job/id (:jdk_ingestion_job/id job)]}))
            (throw e))
          (finally
            (jdbc/execute! db (h/format {:update :jdk_ingestion_job
                                         :set {:jdk_ingestion_job/finished_at [(str (OffsetDateTime/now))]}
                                         :where  [:= :jdk_ingestion_job/id (:jdk_ingestion_job/id job)]}))))
        (println "No Jobs Found")))
    (catch Exception e
      (.printStackTrace e))))