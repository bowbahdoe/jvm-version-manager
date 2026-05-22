(ns user
  (:require [clojure.string :as string]
            [dev.mccue.system :as system]))

(defonce system nil)

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

(defn server
  []
  (:system/server system))

(defn admin-db
  []
  (:system/admin-db system))

(defn worker
  []
  (:system/worker system))

(defn split-classes
  [str]
  (list 'classes (vec (string/split str #" "))))