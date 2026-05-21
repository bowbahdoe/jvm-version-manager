(ns dev.mccue.system
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [dev.mccue.server :as server]
            [dev.mccue.workers :as workers]
            [dev.mccue.session-store :as session-store]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [proletarian.protocols :as protocols]
            [proletarian.worker :as worker]
            [ring.adapter.jetty :as jetty])
  (:import (com.zaxxer.hikari HikariDataSource)
           (net.ttddyy.dsproxy.listener.logging SLF4JLogLevel)
           (net.ttddyy.dsproxy.support ProxyDataSourceBuilder)
           (org.eclipse.jetty.server Server)))


(defn start-server!
  [system]
  (jetty/run-jetty
    (partial #'server/handler system)

    {:port  (or (some-> (System/getenv "PORT") (parse-long))
                8999)
     :join? false}))

(defn stop-server!
  [server]
  (Server/.stop server))

(defn start-admin-db!
  [{:system/keys []}]
  (let [db (jdbc/get-datasource {:dbtype "postgres"
                                 :host (System/getenv "POSTGRES_HOST")
                                 :dbname (or (System/getenv "POSTGRES_DB") "postgres")
                                 :username (or (System/getenv "POSTGRES_USER") "postgres")
                                 :password (or (System/getenv "POSTGRES_PASSWORD") "postgres")})]
    (-> (ProxyDataSourceBuilder/create db)
        (ProxyDataSourceBuilder/.logQueryBySlf4j SLF4JLogLevel/INFO)
        (ProxyDataSourceBuilder/.build))))

(defn stop-admin-db!
  [db]
  (.close db))

(defn start-db!
  [{:system/keys []}]
  (let [db (connection/->pool HikariDataSource
                              {:dbtype "postgres"
                               :host (System/getenv "POSTGRES_HOST")
                               :dbname (or (System/getenv "POSTGRES_DB") "postgres")
                               :username (or (System/getenv "POSTGRES_USER") "postgres")
                               :password (or (System/getenv "POSTGRES_PASSWORD") "postgres")})]
    (-> (ProxyDataSourceBuilder/create db)
        (ProxyDataSourceBuilder/.logQueryBySlf4j SLF4JLogLevel/DEBUG)
        (ProxyDataSourceBuilder/.build))))

(defn stop-db!
  [db]
  (.close db))

(defn log-level
  [x]
  (case x
    ::worker/queue-worker-shutdown-error :error
    ::worker/handle-job-exception-with-interrupt :error
    ::worker/handle-job-exception :error
    ::worker/job-worker-error :error
    ::worker/polling-for-jobs :debug
    :proletarian.retry/not-retrying :error
    :info))

(defn logger
  [x data]
  (log/logp (log-level x) x data))

(defn start-worker!
  [{:system/keys [db] :as system}]
  (doto
    (worker/create-queue-worker
      db
      (partial #'workers/handle-job! system)
      {:proletarian/log #'logger
       :proletarian/serializer (reify protocols/Serializer
                                 (encode [_ data]
                                   (cheshire/generate-string data))
                                 (decode [_ data-string]
                                   (cheshire/parse-string data-string keyword)))})
    (worker/start!)))

(defn stop-worker!
  [worker]
  (worker/stop! worker))

(defn start-session-store!
  [{:system/keys [db]}]
  (session-store/->JDBCSessionStore db))

(defn start!
  []
  (let [db (start-db! {})
        admin-db (start-admin-db! {})
        session-store (start-session-store! {:system/db db})
        server (start-server! {:system/db db
                               :system/session-store session-store})
        worker (start-worker! {:system/db db})]
    {:system/db db
     :system/admin-db admin-db
     :system/session-store session-store
     :system/server server
     :system/worker worker}))

(defn stop!
  [{:system/keys [server worker db]}]
  (stop-worker! worker)
  (stop-server! server)
  (stop-admin-db! db)
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