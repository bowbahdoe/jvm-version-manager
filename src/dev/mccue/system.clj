(ns dev.mccue.system
  (:require [dev.mccue.repository.ingestion :as ingestion]
            [dev.mccue.server :as server]
            [next.jdbc.connection :as connection]
            [ring.adapter.jetty :as jetty])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.util.concurrent Executors ScheduledExecutorService TimeUnit)
           (net.ttddyy.dsproxy.listener.logging SLF4JLogLevel)
           (net.ttddyy.dsproxy.support ProxyDataSourceBuilder)
           (org.eclipse.jetty.server Server)))


(defn start-server!
  [{:system/keys [db]}]
  (jetty/run-jetty
    (partial #'server/handler {:system/db db})
    {:port  (or (some-> (System/getenv "PORT") (parse-long))
                8999)
     :join? false}))

(defn stop-server!
  [server]
  (Server/.stop server))

(defn start-db!
  [{:system/keys []}]
  (let [db (connection/->pool HikariDataSource
                              {:dbtype "postgres"
                               :host (System/getenv "POSTGRES_HOST")
                               :dbname (or (System/getenv "POSTGRES_DB") "postgres")
                               :username (or (System/getenv "POSTGRES_USER") "postgres")
                               :password (or (System/getenv "POSTGRES_PASSWORD") "postgres")})]
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