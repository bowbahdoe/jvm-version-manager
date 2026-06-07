(ns dev.mccue.atproto.did-cache
  (:require [dev.mccue.atproto.diddy :as diddy])
  (:import (com.github.benmanes.caffeine.cache Cache Caffeine)
           (java.time Duration)))

(defn create
  []
  (-> (Caffeine/newBuilder)
      (.expireAfterWrite (Duration/ofHours 1))
      (.build)))

(defn get-handle
  [cache did]
  (if-let [handle (Cache/.getIfPresent cache did)]
    handle
    (if-let [handle (some-> (diddy/resolve-did-document did)
                            (diddy/resolve-handle))]
      (do (doto cache
              (Cache/.put did handle)
              (Cache/.put handle did))
          handle)
      nil)))


