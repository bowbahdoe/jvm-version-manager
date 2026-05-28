(ns dev.mccue.publish.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :as page-helpers]))

(defn get-publish-handler
  [system request]
  (page-helpers/page-response
    :title "Publish"
    :body (sidebar-components/sidebar
            request
            "Hello!")))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/publish" {:get (partial #'get-publish-handler system)}]]])