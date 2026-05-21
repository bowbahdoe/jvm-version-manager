(ns dev.mccue.environment)

(defn development?
  []
  (= (System/getenv "ENVIRONMENT") "development"))


(defn production?
  []
  (not (development?)))