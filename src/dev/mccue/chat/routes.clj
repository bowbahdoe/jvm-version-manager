(ns dev.mccue.chat.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :refer [page-response]]
            [dev.mccue.sidebar.components :as sidebar-components]))


(defn placeholder-handler
  [{:system/keys [db]} request]
  (page-response
    :title "Chat"
    :body (sidebar-components/sidebar
            request
            [:div])))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   ["/chat" {:get (partial #'placeholder-handler system)}]
   #_["/search" {:get (partial #'get-profile-handler system)}]])
