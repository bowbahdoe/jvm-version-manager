(ns dev.mccue.jetstream
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.net URI)
           (org.java_websocket.client WebSocketClient)))

(defn- on-open
  [system server-handshake]
  (log/info "Opening websocket connection"))

(defn- on-message
  [{:system/keys [db]} message]
  (try
    (log/trace "New Event: " message)
    (let [parsed-event (cheshire/parse-string message)
          insert!       (fn []
                          (jdbc/execute! db (sql/format
                                              {:insert-into :atproto.jetstream_event
                                               :columns [:event]
                                               :values [[[[:jsonb message]]]]})))
          event-kind (get parsed-event "kind")]
      (if (#{"account" "identity"} event-kind)
        ;; We only care about account/identity events for users
        ;; in our system.
        (if-let [atproto_did (get-in parsed-event [event-kind "did"])]
          (if (seq (jdbc/execute! db (sql/format
                                       {:select [:id]
                                        :from :identity.user
                                        :where [:= :atproto_did atproto_did]})))
            (insert!)
            (log/debug "Got account event for user we don't have. " atproto_did))
          (log/error "No did in account event? " parsed-event))
        ;; All other events came from event types we probably created
        (insert!)))
    (catch Throwable t
      (log/error t "Error processing jetstream message"))))

(defn- on-close
  [system code reason remote]
  (log/info "Closing websocket connection"))

(defn- on-error
  [system exception]
  (log/error exception "Error with websocket connection"))

(defn create-websocket-client
  [system uri]
  (proxy [WebSocketClient] [uri]
    (onOpen [server-handshake]
      (on-open system server-handshake))
    (onMessage [message]
      (on-message system message))
    (onClose [code reason remote]
      (on-close system code reason remote))
    (onError [exception]
      (on-error system exception))))

(defn start-jetstream-websocket-client!
  [system]
  (let [ws-client (create-websocket-client system
                                           (URI. (str (System/getenv "JETSTREAM_URL")
                                                      "/subscribe?wantedCollections=dev.fudgeu.experimental.atforumv1.forum.identity")))]
    (WebSocketClient/.connectBlocking ws-client)
    ws-client))

(defn stop-jetstream-websocket-client!
  [client]
  (WebSocketClient/.closeBlocking client))