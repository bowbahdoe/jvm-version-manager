(ns dev.mccue.profile.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [page-response classes]]
            [dev.mccue.sidebar.components :as sidebar-components]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response]))

(defn discord-account-section
  [discord-linked-accounts]
  [:div {:class (classes ["flex" "flex-col" "gap-3"])}
   [:h2 {:class (classes ["text-lg"])} "Linked Discord Accounts"]
   (for [account discord-linked-accounts]
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
       [:p (:discord_username account)]
       [:form {:method "POST"
               :action (str "/profile/unlink_discord/"
                            (:id account))
               :class (classes ["px-2"
                                "py-1"
                                "text-sm"

                                "outline-2"
                                "hover:bg-red-200"
                                "w-fit"

                                "cursor-pointer"])}
        (hiccup/raw (anti-forgery/anti-forgery-field))
        [:input {:class (classes ["cursor-pointer"])
                 :type "submit"
                 :value "-"
                 "@click" "if(!confirm('Are you sure?')) $event.preventDefault()"}]]]])
   [:a {:href  "/oauth2/discord"
        :class (classes ["px-2"
                         "py-1"
                         "text-sm"

                         "outline-2"
                         "hover:bg-green-200"
                         "w-fit"])}
    "+"]])

(defn github-account-section
  [github-linked-accounts]
  [:div {:class (classes ["flex" "flex-col" "gap-3"])}
   (list


     [:h2 {:class (classes ["text-lg"])} "Linked Github Accounts"]
     (for [account github-linked-accounts]
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
         [:p (:github_username account)]
         [:form {:method "POST"
                 :action (str "/profile/unlink_github/"
                              (:id account))
                 :class (classes ["px-2"
                                  "py-1"
                                  "text-sm"

                                  "outline-2"
                                  "hover:bg-red-200"
                                  "w-fit"

                                  "cursor-pointer"])}
          (hiccup/raw (anti-forgery/anti-forgery-field))
          [:input {:class (classes ["cursor-pointer"])
                   :type "submit"
                   :value "-"
                   "@click" "if(!confirm('Are you sure?')) $event.preventDefault()"}]]]])
     [:a {:href  "/oauth2/github"
          :class (classes ["px-2"
                           "py-1"
                           "text-sm"
                           "outline-2"
                           "hover:bg-green-200"
                           "w-fit"])} "+"])])

(defn get-profile-handler
  [{:system/keys [db]} request]
  (page-response
    :title "Profile"
    :body (sidebar-components/sidebar
            request
            [:div {:class "mt-10 flex flex-row items-center justify-center gap-x-6 gap-y-6"}]
            (let [user (:identity request)]
              (let [user-info (jsonquery/execute-one!
                                db
                                {:select [:id :profile_image_png_base64
                                          :atproto_handle
                                          :atproto_did
                                          [:discord_linked_accounts
                                           {:select  [:id
                                                      :discord_username
                                                      :discord_profile_image_png_base64]
                                            :from    :discord.linked_account
                                            :join-on [:id :user_id]}]
                                          [:github_linked_accounts
                                           {:select  [:id
                                                      :github_username
                                                      :github_profile_image_png_base64]
                                            :from    :github.linked_account
                                            :join-on [:id :user_id]}]
                                          ^:single
                                          [:atproto_access_credential
                                           {:select  [:did
                                                      :access_token
                                                      :refresh_token
                                                      :service_endpoint]
                                            :from    :atproto.access_credential
                                            :join-on [:atproto_did :did]}]]


                                 :from   :identity.user
                                 :where  [:= :id (:user/id user)]})]
                [:div {:class (classes ["flex" "flex-col" "m-3" "gap-6"])}
                 (list
                   [:div
                    {:class (page-helpers/classes ["flex"
                                                   "flex-col"
                                                   "items-center"
                                                   "p-7"
                                                   "rounded-2xl"
                                                   "gap-4"])}

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
                   (discord-account-section (:discord_linked_accounts user-info))
                   (github-account-section (:github_linked_accounts user-info)))])))))

(defn post-unlink-discord-handler
  [{:system/keys [db]} request]
  (let [{:keys [linked-discord-account-id]} (:path-params request)]
    (jdbc/execute! db
                   (sql/format
                     {:delete-from :discord.linked_account
                      :where [:and
                              [:= :id (parse-uuid linked-discord-account-id)]
                              [:= :user_id (:user/id (:identity request))]]})))

  (response/redirect "/profile"))

(defn post-unlink-github-handler
  [{:system/keys [db]} request]
  (let [{:keys [linked-github-account-id]} (:path-params request)]
    (jdbc/execute! db
                   (sql/format
                     {:delete-from :github.linked_account
                      :where [:and
                              [:= :id (parse-uuid linked-github-account-id)]
                              [:= :user_id (:user/id (:identity request))]]})))

  (response/redirect "/profile"))


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
   ["/profile/unlink_discord/:linked-discord-account-id"
    {:post (partial #'post-unlink-discord-handler system)}]
   ["/profile/unlink_github/:linked-github-account-id"
    {:post (partial #'post-unlink-github-handler system)}]
   ["/answer" {:get (partial #'placeholder-handler system)}]
   #_["/search" {:get (partial #'get-profile-handler system)}]])