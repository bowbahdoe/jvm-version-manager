(ns dev.mccue.workers
  (:require [clojure.tools.logging :as log]
            [dev.mccue.atproto.workers :as atproto-workers]
            [dev.mccue.auth.workers :as auth-workers]
            [honey.sql :as sql]))

(defn handle-job!
  [system job-type payload]
  (let [handlers (merge (auth-workers/workers)
                        (atproto-workers/workers))
        handler  (get handlers job-type)]
    (log/info "Handling Job:" job-type)
    (if handler
      (handler system job-type payload)
      (log/error "Could not find handler for Job:" job-type))))

(defn manual-trigger
  [db & {:keys [table job-type id]}]
  (sql/format
    {:insert-into :proletarian.job
     :columns [:job_type :payload]
     :values [[(str job-type)
               [[:row_to_json {:select [:*]
                               :from table
                               :where [:= :id id]}]]]]}))