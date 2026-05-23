(ns dev.mccue.index.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]))


(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body [:div
           [:div {:class (classes ["flex"
                                   "h-screen"])}
            [:aside {:class (classes ["w-60 border-r-4 bg-white flex flex-col"])}
             [:nav {:class (classes ["flex-1 px-3 space-y-4"])}
              [:a {:class (classes ["flex items-center gap-3 px-4 py-2 my-4"
                                    "justify-center"
                                    "px-3.5"
                                    "py-2.5"
                                    "text-sm"
                                    "font-semibold"
                                    "text-black"
                                    "shadow-xs"
                                    "outline-2"
                                    "hover:outline-2"
                                    "hover:outline-offset-2"
                                    "hover:outline-black"
                                    "focus-visible:outline-2"
                                    "focus-visible:outline-offset-2"
                                    "focus-visible:outline-black"])}
               "Ask"]
              [:a {:class (classes ["flex items-center gap-3 px-4 py-2 my-4"
                                    "justify-center"
                                    "px-3.5"
                                    "py-2.5"
                                    "text-sm"
                                    "font-semibold"
                                    "text-black"
                                    "shadow-xs"
                                    "outline-2"
                                    "hover:outline-2"
                                    "hover:outline-offset-2"
                                    "hover:outline-black"
                                    "focus-visible:outline-2"
                                    "focus-visible:outline-offset-2"
                                    "focus-visible:outline-black"])}
               "Answer"]
              [:a {:class (classes ["flex items-center gap-3 px-4 py-2 my-4"
                                    "justify-center"
                                    "px-3.5"
                                    "py-2.5"
                                    "text-sm"
                                    "font-semibold"
                                    "text-black"
                                    "shadow-xs"
                                    "outline-2"
                                    "hover:outline-2"
                                    "hover:outline-offset-2"
                                    "hover:outline-black"
                                    "focus-visible:outline-2"
                                    "focus-visible:outline-offset-2"
                                    "focus-visible:outline-black"])}
               "Search"]
              [:a {:class (classes ["flex items-center gap-3 px-4 py-2 my-4"
                                    "justify-center"
                                    "px-3.5"
                                    "py-2.5"
                                    "text-sm"
                                    "font-semibold"
                                    "text-black"
                                    "shadow-xs"
                                    "outline-2"
                                    "hover:outline-2"
                                    "hover:outline-offset-2"
                                    "hover:outline-black"
                                    "focus-visible:outline-2"
                                    "focus-visible:outline-offset-2"
                                    "focus-visible:outline-black"])}
               "Chat"]]
             #_[:nav {:class (classes ["flex-1 px-3 space-y-1"])}]]

            [:main {:class (classes ["flex-1 overflow-y-auto"])}

             [:div {:class "mt-10 flex flex-row items-center justify-center gap-x-6 gap-y-6"}
              (when-not (:user request)
                [:a {:href  "/not-oauth/atproto"
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
                                      "focus-visible:outline-black"])} "@Login"])

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
                                      "focus-visible:outline-black"])} "@Login (OAuth)"])
              (when (:user request)
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
                                      "focus-visible:outline-black"])} "Link GitHub Account"])
              (when (:user request)
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
                                      "focus-visible:outline-black"])} "Link Discord Account"])


              (when (:user request)
                [:a {:href  "/logout"
                     :class (page-helpers/classes ["rounded-md"
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
                 "Logout"])]

             (when-let [user (:user request)]
               (let [user-info (jsonquery/execute-one! db {:select [:id :profile_image_png_base64
                                                                    [:discord_linked_accounts
                                                                     {:select [:discord_username
                                                                               :discord_profile_image_png_base64]
                                                                      :from :discord.linked_account
                                                                      :join-on [:id :user_id]}]
                                                                    [:github_linked_accounts
                                                                     {:select [:github_username
                                                                               :github_profile_image_png_base64]
                                                                      :from :github.linked_account
                                                                      :join-on [:id :user_id]}]]

                                                           :from   :identity.user
                                                           :where  [:= :id (:user/id user)]})]
                 (list
                   [:div
                    {:class (page-helpers/classes ["flex"
                                                   "flex-col"
                                                   "items-center"
                                                   "p-7"
                                                   "rounded-2xl"])}
                    [:div
                     [:img
                      {:class  (classes ["size-48"
                                         "rounded-md"
                                         "outline-4"
                                         "outline-offset-2"
                                         "outline-black"])
                       :src    (str "data:image/png;base64, " (:profile_image_png_base64 user-info))
                       :width  64
                       :height 64
                       :style  "image-rendering: pixelated"}]]]
                   [:h2 "Linked Discord Accounts"
                    (for [account (:discord_linked_accounts user-info)]
                      [:div
                       {:class (page-helpers/classes ["flex"
                                                      "flex-col"
                                                      "items-center"
                                                      "p-7"
                                                      "rounded-2xl"])}
                       [:div {:class (classes ["flex" "flex-row items-center justify-center gap-x-6 gap-y-6"])}
                        [:p (:discord_username account)]
                        [:img
                         {:class  (classes ["size-32"
                                            "rounded-md"
                                            "outline-1"
                                            "outline-offset-2"
                                            "outline-black"])
                          :src    (str "data:image/png;base64, " (:discord_profile_image_png_base64 account))
                          :width  32
                          :height 32
                          :style  "image-rendering: pixelated"}]]])]
                   [:h2 "Linked Github Accounts"
                    (for [account (:github_linked_accounts user-info)]
                      [:div
                       {:class (page-helpers/classes ["flex"
                                                      "flex-col"
                                                      "items-center"
                                                      "p-7"
                                                      "rounded-2xl"])}
                       [:div {:class (classes ["flex" "flex-row items-center justify-center gap-x-6 gap-y-6"])}
                        [:p (:github_username account)]
                        [:img
                         {:class  (classes ["size-32"
                                            "rounded-md"
                                            "outline-1"
                                            "outline-offset-2"
                                            "outline-black"])

                          :src    (str "data:image/png;base64, " (:github_profile_image_png_base64 account))
                          :width  32
                          :height 32
                          :style  "image-rendering: pixelated"}]]])])))]]]))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]]])
