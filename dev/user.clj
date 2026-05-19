(ns user
  (:require [dev.mccue.repository.system :as system]))

(def system nil)

(defn start!
  []
  (alter-var-root #'system (fn [& _] (system/start!))))

(defn stop!
  []
  (when system
    (system/stop! system)
    (alter-var-root #'system (fn [& _] nil))))

(defn restart!
  []
  (stop!)
  (start!))

(defn db
  []
  (:system/db system))