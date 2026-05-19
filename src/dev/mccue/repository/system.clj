(ns dev.mccue.repository.system
  (:require [dev.mccue.repository.server :as server]
            [dev.mccue.repository.ingestion :as ingestion]
            [next.jdbc.connection :as connection]
            [ring.adapter.jetty :as jetty])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.time.temporal ChronoUnit)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)
           (net.ttddyy.dsproxy.listener.logging SLF4JLogLevel)
           (net.ttddyy.dsproxy.support ProxyDataSourceBuilder)
           (org.eclipse.jetty.server Server)
           (javax.sql DataSource)))


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
  (let [db (connection/->pool HikariDataSource
                              {:dbtype "postgres"
                               :dbname "postgres"
                               :username "postgres"
                               :password "postgres"})]
    (-> (ProxyDataSourceBuilder/create db)
        (ProxyDataSourceBuilder/.logQueryBySlf4j SLF4JLogLevel/INFO)
        (ProxyDataSourceBuilder/.build))))

(defn stop-db!
  [db]
  (.close db))

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
        worker nil #_(start-worker! {:system/db db})]
    {:system/db db
     :system/server server
     :system/worker worker}))

(defn stop!
  [{:system/keys [server worker db]}]
  #_(stop-worker! worker)
  (stop-server! server)
  (stop-db! db))

(comment
  (def system nil)

  (alter-var-root #'system (fn [& _] (start!)))

  (do
    (stop! system)
    (alter-var-root #'system (fn [& _] nil))))

(defn -main
  [& args]
  (start!))