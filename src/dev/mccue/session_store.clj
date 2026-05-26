(ns dev.mccue.session-store
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.middleware.session.store :as session-store])
  (:import
    (com.fasterxml.uuid Generators)
    (java.time Duration OffsetDateTime)
    (java.util UUID)
    (org.postgresql.util PGobject)))

(defn- read-session-impl
  [db key]
  (log/debug "Reading session " key)
  (let [key (try (UUID/fromString key)
                 (catch IllegalArgumentException _ nil)
                 (catch NullPointerException _ nil))]
    (some-> (jdbc/execute-one!
              db
              (sql/format {:select [:id :data]
                           :from :identity.session
                           :where [:and
                                   [:= :id key]
                                   [:= :invalidated_at nil]
                                   [:> :expires_at [[:now]]]]}))
            (update :session/data #(cheshire/parse-string (str %) keyword))
            (:session/data))))

(defn- write-session-impl
  [db key data]
  (if (nil? key)
    (let [session-id (.generate (Generators/timeBasedEpochGenerator))]
      (jdbc/execute! db (sql/format
                          {:insert-into :identity.session
                           :columns     [:id :data :expires_at]
                           :values      [[session-id
                                          (doto (PGobject.)
                                            (.setType "jsonb")
                                            (.setValue
                                              (cheshire/generate-string data)))
                                          (OffsetDateTime/.plus
                                            (OffsetDateTime/now)
                                            (Duration/ofHours 24))]]}))
      session-id)

    (do
      (jdbc/execute! db (sql/format
                          {:update :identity.session
                           :set    {:expires_at [[:raw "now() + interval '24 hours'"]]
                                    :data (doto (PGobject.)
                                            (.setType "jsonb")
                                            (.setValue
                                              (cheshire/generate-string data)))}
                           :where  [:= :id (UUID/fromString key)]}))
      key)))

(defn delete-session-impl
  [db key]
  (jdbc/execute! db (sql/format
                      {:update :identity.session
                       :set {:invalidated_at [[:now]]}
                       :where  [:= :id (UUID/fromString key)]}))
  nil)

(defrecord JDBCSessionStore
  [db]
  session-store/SessionStore
  (read-session [_ key]
    (read-session-impl db key))

  (write-session [_ key data]
    (write-session-impl db key data))

  (delete-session [_ key]
    (delete-session-impl db key)))

