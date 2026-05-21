(ns dev.mccue.qa.routes
  (:require [dev.mccue.middleware :as middleware]))

(defn get-qa-handler
  [system request])

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/qa" {:get (partial #'get-qa-handler system)}]]])