(ns dev.mccue.backfill.jobs
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
            [dev.mccue.atproto.diddy :as diddy])
  (:import (java.util.concurrent Executors Semaphore)))

;; Backfill based on HappyView
;; https://happyview.dev/guides/backfill

(defn get-repos
  [{:keys [collection limit cursor]}] collection
  (-> (http/get (str "https://relay1.us-east.bsky.network"
                     "/xrpc/com.atproto.sync.listReposByCollection")
                {:query-params (cond-> {:collection collection}
                                       limit  (assoc :limit limit)
                                       cursor (assoc :cursor cursor))})
      (:body)
      (json/parse-string-strict keyword)))

(defn get-all-repos
  [{:keys [collection limit]}]
  (apply concat
         (iteration
           (fn [cursor]
             (get-repos (cond-> {:collection collection}
                                cursor (assoc :cursor cursor)
                                limit  (assoc :limit  limit))))
           {:vf :repos
            :kf :cursor})))

(defn get-records
  [{:keys [service-endpoint cursor repo collection limit]}]
  (-> (http/get (str service-endpoint
                   "/xrpc/com.atproto.repo.listRecords")
              {:query-params (cond-> {:repo       repo
                                      :collection collection}
                                     cursor (assoc :cursor cursor)
                                     limit  (assoc :limit  limit))})
      (:body)
      (json/parse-string-strict keyword)))

(defn get-all-records
  [{:keys [service-endpoint repo collection limit]}]
  (apply concat
         (iteration
           (fn [cursor]
             (get-records (cond-> {:service-endpoint service-endpoint
                                   :repo repo
                                   :collection collection}
                                  cursor (assoc :cursor cursor)
                                  limit  (assoc :limit  limit))))
           {:vf :records
            :kf :cursor})))

(defn backfill-records!
  [{:system/keys [db]}]
  (let [repos (get-all-repos {:collection "dev.mccue.jvm.module"})]
    (doseq [{:keys [did]} repos]
      (let [did-document     (diddy/resolve-did-document did)
            service-endpoint (diddy/resolve-service-endpoint did-document)]
        (doseq [record (get-all-records {:service-endpoint service-endpoint
                                         :repo did
                                         :collection "dev.mccue.jvm.module"})]
          (println record))))))

(backfill-records! {})
(set-agent-send-executor! (Executors/newVirtualThreadPerTaskExecutor))
(set-agent-send-off-executor! (Executors/newVirtualThreadPerTaskExecutor))

(comment
  (let [sp (Semaphore. 32)]
    (->>
      (map
        (fn [{:keys [did]}]
          (future
            (Semaphore/.acquire sp)
            (try
              (println did)
              (-> did
                  (diddy/resolve-did-document)
                  (diddy/resolve-handle))
              (finally
                (Semaphore/.release sp)))))
        (get-all-repos {:collection "app.bsky.actor.profile"
                        :limit      2000}))
      (map (fn [f] @f)))))

(comment
  (get-all-repos {:collection "app.bsky.actor.profile"
                  :limit     2000}))

