(ns dev.mccue.index.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]
            [dev.mccue.sidebar.components :as sidebar-components]))


(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body (sidebar-components/sidebar
            request
            (when-not (:user request)
              [:a {:href  "/oauth2/atproto/launch/mccue.dev"
                   :class (classes ["rounded-md"
                                    "bg-black"
                                    "px-3.5"
                                    "py-2.5"
                                    "text-sm"
                                    "font-semibold"
                                    "text-white"
                                    "shadow-xs"
                                    "hover:outline-2"
                                    "hover:outline-offset-2"
                                    "hover:outline-black"
                                    "focus-visible:outline-2"
                                    "focus-visible:outline-offset-2"
                                    "focus-visible:outline-black"])} "@Login (OAuth)"]))))


(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]]])
