(ns dev.mccue.archives.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :as page-helpers]))

(defn get-archives-handler
  [system request]
  (page-helpers/page-response
    :title "Archives"
    :body (sidebar-components/sidebar
            request
            "Hello!")))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/archives" {:get (partial #'get-archives-handler system)}]]])