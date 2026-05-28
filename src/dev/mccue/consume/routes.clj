(ns dev.mccue.consume.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :as page-helpers]))

(defn get-consume-handler
  [system request]
  (page-helpers/page-response
    :title "Consume"
    :body (sidebar-components/sidebar
            request
            "Hello!")))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/consume" {:get (partial #'get-consume-handler system)}]]])