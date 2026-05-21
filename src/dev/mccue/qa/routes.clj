(ns dev.mccue.qa.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]))

(defn get-qa-handler
  [system request]
  (page-helpers/page-response
    :title "QA"
    :body "Hello!"))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/qa" {:get (partial #'get-qa-handler system)}]]])