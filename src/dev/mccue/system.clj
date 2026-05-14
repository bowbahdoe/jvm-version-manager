(ns dev.mccue.system
  (:require [dev.mccue.repository :as repository]
            [dev.mccue.server :as server]
            [dev.mccue.ingestion :as ingestion]
            [ring.adapter.jetty :as jetty])
  (:import (java.time.temporal ChronoUnit)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)
           (org.eclipse.jetty.server Server)))


(defn start-server!
  [{:system/keys [db]}]
  (jetty/run-jetty
    (partial #'server/handler {:system/db db})
    {:port  8999
     :join? false}))

(defn stop-server!
  [server]
  (Server/.stop server))

(defn start-db!
  [{:system/keys []}]
  (repository/from-file "modules.db"))

(defn start-worker!
  [system]
  (let [executor (Executors/newScheduledThreadPool 1)]
    (ScheduledExecutorService/.scheduleAtFixedRate
      executor
      (partial #'ingestion/fetch-jdk system)
      0
      10
      TimeUnit/SECONDS)
    {:executor executor}))

(defn stop-worker!
  [worker]
  (ScheduledExecutorService/.shutdown (:executor worker)))

(defn start!
  []
  (let [db (start-db! {})
        server (start-server! {:system/db db})
        worker (start-worker! {:system/db db})]
    {:system/db db
     :system/server server
     :system/worker worker}))

(defn stop!
  [{:system/keys [server worker]}]
  (stop-worker! worker)
  (stop-server! server))

(comment
  (def system nil)

  (alter-var-root #'system (fn [& _] (start!)))

  (do
    (stop! system)
    (alter-var-root #'system (fn [& _] nil))))