(ns dev.mccue.home.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]
            [dev.mccue.sidebar.components :as sidebar-components]
            [next.jdbc :as jdbc]))


(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "Home"
    :body (sidebar-components/sidebar
            request
            [:main
             [:div {:class (classes ["flex" "flex-col"])}
              [:code [:pre (with-out-str
                             (clojure.pprint/pprint
                               (jdbc/execute!
                                 db
                                 ["SELECT * FROM identity.user WHERE id = ?"
                                  (:user/id (:identity request))])))]]]])))



(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]]])
