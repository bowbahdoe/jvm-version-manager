(ns dev.mccue.repository.ingestion
  (:require [clojure.tools.logging :as log]
            [honey.sql :as h]
            [next.jdbc :as jdbc]
            [dev.mccue.repository.jmod :as jmod]
            [dev.mccue.repository.repository :as repository]
            [dev.mccue.repository.artifact :as artifact]
            [next.jdbc.transaction])
  (:import (java.time OffsetDateTime)))

(defn fetch-jdk
  [{:system/keys [db]}]
  (log/info "Fetching JDK")
  (try
    (binding [next.jdbc.transaction/*nested-tx* :ignore]
      (if-let [job (jdbc/execute-one! db (h/format {:select [:id
                                                             :windows_amd64_url
                                                             :windows_amd64_sha256_url
                                                             :windows_amd64_sha256
                                                             :macos_aarch64_url
                                                             :macos_aarch64_sha256_url
                                                             :macos_aarch64_sha256
                                                             :linux_aarch64_url
                                                             :linux_aarch64_sha256_url
                                                             :linux_aarch64_sha256
                                                             :linux_amd64_url
                                                             :linux_amd64_sha256_url
                                                             :linux_amd64_sha256
                                                             :user_id]
                                                    :from :repository.jdk_ingestion_job
                                                    :where [:= :jdk_ingestion_job/finished_at nil]
                                                    :limit 1}))]
        (try
          (jdbc/execute! db (h/format {:update :repository.jdk_ingestion_job
                                       :set {:jdk_ingestion_job/started_at [(str (OffsetDateTime/now))]}
                                       :where  [:= :jdk_ingestion_job/id (:repository.jdk_ingestion_job/id job)]}))

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
                artifacts (jmod/procure {:fetch (partial #'artifact/fetch-artifact-cached db)}
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
            (jdbc/execute! db (h/format {:update :repository.jdk_ingestion_job
                                         :set {:jdk_ingestion_job/error (.getMessage e)}
                                         :where [:= :jdk_ingestion_job/id (:repository.jdk_ingestion_job/id job)]}))
            (throw e))
          (finally
            (jdbc/execute! db (h/format {:update :repository.jdk_ingestion_job
                                         :set {:jdk_ingestion_job/finished_at [(str (OffsetDateTime/now))]}
                                         :where  [:= :jdk_ingestion_job/id (:repository.jdk_ingestion_job/id job)]}))))
        (println "No Jobs Found")))
    (catch Exception e
      (.printStackTrace e))))