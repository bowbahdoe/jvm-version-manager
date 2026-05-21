(ns dev.mccue.register.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]
            [dev.mccue.jsonquery :as jsonquery]))

(defn get-register-handler
  [system request]
  (page-helpers/page-response
    :body
    (list
      [:div
       [:h1 "Login"]
       [:form
        {:action "POST"}
        [:label {:for "username"}
         "Username"]
        [:br]
        [:input {:id "username" :type "text" :required true}]
        [:br]
        [:label {:for "password"}
         "Password"]
        [:br]
        [:input {:id "password" :type "password" :required true}]
        [:br]
        [:input {:type "submit"}]]]
      [:div
       [:h1 "Register"]
       [:form
        {:action "POST"}
        [:label {:for "username"}
         "Username"]
        [:br]
        [:input {:id "username" :type "text"  :required true}]
        [:br]
        [:label {:for "password"}
         "Password"]
        [:br]
        [:input {:id "password" :type "password"  :required true}]
        [:br]
        [:label {:for "invite-code"}
         "Invite Code"]
        [:br]
        [:input {:id "invite-code" :type "text"  :required true}]
        [:br]
        [:input {:type "submit"}]]])))

(defn get-users-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body [:ul
           (for [user (jsonquery/execute! db {:select [:id :profile_image_png_base64]
                                              :from :identity.user})]
             [:li [:img {:src (str "data:image/png;base64, " (:profile_image_png_base64 user))
                         :width 128
                         :height 128
                         :style "image-rendering: pixelated"}]
              [:p (:id user)]])]))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/register" {:get (partial #'get-register-handler system)}]]
   [["/users" {:get (partial #'get-users-handler system)}]]])