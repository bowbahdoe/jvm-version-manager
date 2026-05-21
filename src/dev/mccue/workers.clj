(ns dev.mccue.workers
  (:require [clojure.tools.logging :as log]
            [dev.mccue.register.workers :as register-workers]))

(defn handle-job!
  [system job-type payload]
  (let [handlers (merge (register-workers/workers))
        handler  (get handlers job-type)]
    (log/info "Handling Job: {}" job-type)
    (if handler
      (handler system job-type payload)
      (log/error "Could not find handler for Job: {}" job-type))))
