(ns dev.mccue.jobs.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :as page-helpers]))

(defn get-jobs-handler
  [system request]
  (page-helpers/page-response
    :title "Jobs"
    :body (sidebar-components/sidebar
            request
            "Hello!")))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/jobs" {:get (partial #'get-jobs-handler system)}]]])