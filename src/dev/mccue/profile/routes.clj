(ns dev.mccue.profile.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [page-response classes]]
            [dev.mccue.sidebar.components :as sidebar-components]))


(defn get-profile-handler
  [{:system/keys [db]} request]
  (page-response
    :title "Profile"
    :body (sidebar-components/sidebar
            request
            [:div {:class "mt-10 flex flex-row items-center justify-center gap-x-6 gap-y-6"}]
            (let [user (:user request)]
              (let [user-info (jsonquery/execute-one!
                                db
                                {:select [:id :profile_image_png_base64
                                          :atproto_handle
                                          :atproto_did
                                          [:discord_linked_accounts
                                           {:select  [:discord_username
                                                      :discord_profile_image_png_base64]
                                            :from    :discord.linked_account
                                            :join-on [:id :user_id]}]
                                          [:github_linked_accounts
                                           {:select  [:github_username
                                                      :github_profile_image_png_base64]
                                            :from    :github.linked_account
                                            :join-on [:id :user_id]}]
                                          ^:single
                                          [:atproto_access_credential
                                           {:select [:did
                                                     :access_token
                                                     :refresh_token
                                                     :service_endpoint]
                                            :from :atproto.access_credential
                                            :join-on [:atproto_did :did]}]]


                                 :from   :identity.user
                                 :where  [:= :id (:user/id user)]})]
                (list
                  [:div
                   {:class (page-helpers/classes ["flex"
                                                  "flex-col"
                                                  "items-center"
                                                  "p-7"
                                                  "rounded-2xl"
                                                  "gap-4"])}

                   [:code [:pre (clojure.pprint/pprint
                                  (try
                                    (:body (clj-http.client/get
                                             (str (:service_endpoint (:atproto_access_credential user-info))
                                                  "/xrpc/app.bsky.actor.getProfile"
                                                  "?actor=" (:did (:atproto_access_credential user-info)))
                                             {:headers {"Authorization"  (str "Bearer " (:access-token (:atproto_access_credential user-info)))
                                                        "Content-Type" "application/json"}}))

                                    (catch Exception e e)))]]
                   [:div {:class (classes ["flex"
                                           "flex-col"
                                           "items-center"])}
                    [:img
                     {:class  (classes ["size-48"
                                        "rounded-md"
                                        "outline-4"
                                        "outline-offset-2"
                                        "outline-black"])
                      :src    (str "data:image/png;base64, " (:profile_image_png_base64 user-info))
                      :width  64
                      :height 64
                      :style  "image-rendering: pixelated"}]
                    [:p (:atproto_handle user-info) " - " (:atproto_did user-info)]]]
                  [:h2 "Linked Discord Accounts"
                   (for [account (:discord_linked_accounts user-info)]
                     [:div
                      {:class (page-helpers/classes ["flex"
                                                     "flex-col"
                                                     "items-start"
                                                     "p-7"
                                                     "rounded-2xl"])}
                      [:div {:class (classes ["flex" "flex-row items-center justify-center gap-x-6 gap-y-6"])}

                       [:img
                        {:class (classes ["rounded-md"
                                          "outline-1"
                                          "outline-offset-2"
                                          "outline-black"
                                          "w-32px"
                                          "h-32px"])
                         :src   (str "data:image/png;base64, " (:discord_profile_image_png_base64 account))
                         :style "image-rendering: pixelated"}]
                       [:p (:discord_username account)]]])
                   [:a {:href  "/oauth2/github"
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
                                         "focus-visible:outline-black"])} "Link GitHub Account"]]
                  [:h2 "Linked Github Accounts"
                   (for [account (:github_linked_accounts user-info)]
                     [:div
                      {:class (page-helpers/classes ["flex"
                                                     "flex-col"
                                                     "items-start"
                                                     "p-7"
                                                     "rounded-2xl"])}
                      [:div {:class (classes ["flex" "flex-row items-center justify-center gap-x-6 gap-y-6"])}
                       [:img
                        {:class (classes ["rounded-md"
                                          "outline-1"
                                          "outline-offset-2"
                                          "outline-black"
                                          "w-32px"
                                          "h-32px"])

                         :src   (str "data:image/png;base64, " (:github_profile_image_png_base64 account))
                         :style "image-rendering: pixelated"}]
                       [:p (:github_username account)]]])

                   [:a {:href  "/oauth2/discord"
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
                                         "focus-visible:outline-black"])}
                    "Link Discord Account"]]))))))


(defn placeholder-handler
  [{:system/keys [db]} request]
  (page-response
    :title "Profile"
    :body (sidebar-components/sidebar
            request
            [:div])))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/profile" {:get (partial #'get-profile-handler system)}]
   ["/answer" {:get (partial #'placeholder-handler system)}]
   #_["/search" {:get (partial #'get-profile-handler system)}]])