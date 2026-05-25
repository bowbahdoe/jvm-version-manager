(ns dev.mccue.atproto.workers
  (:require [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.util UUID)))

(defn atproto_jetstream_event-processEvent
  [{:system/keys [db]} _job-type payload]
  (condp = (:kind (:event payload))
    "account" (do
                (log/debug "Account Event: cleaning up")
                (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                               :where [:= :id (UUID/fromString (:id payload))]})))
    "identity" (do
                 (log/debug "Identity Event: cleaning up")
                 (jdbc/execute! db (sql/format {:delete-from :atproto.jetstream_event
                                                :where [:= :id (UUID/fromString (:id payload))]})))
    (log/info (str "Unhandled Event: " payload))))

(defn workers
  []
  {:atproto.jetstream_event/processEvent #'atproto_jetstream_event-processEvent})