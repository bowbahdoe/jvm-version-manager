(ns dev.mccue.auth.routes
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.auth.duke :as duke]
            [dev.mccue.environment :as environment]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes page-response]]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.middleware.oauth2 :as oauth2 :refer [wrap-oauth2]]
            [clj-http.client :as clj-http-client]
            [ring.util.response :as response])

  (:import (dev.mccue.duke Duke Seed)
           (java.util Base64 Base64$Encoder Hashtable)
           (javax.naming NamingEnumeration)
           (javax.naming.directory DirContext InitialDirContext Attributes Attribute)))


(defn dummy-route-handler
  [request]
  (log/error
    "Dummy Route Handler Called. Should have been intercepted by oauth middleware. {}"
    request)
  {:status 500
   :body   "Internal Error"})


(defn get-github-landing-handler
  [{:system/keys [db]} request]
  (let [{:keys [token]} (:github (::oauth2/access-tokens (:session request)))]
    (let [response (clj-http-client/get "https://api.github.com/user"
                                  {:headers {"Authorization" (str "Bearer " token)}})
          github-user-id (get (cheshire/parse-string (:body response)) "id")]
      (jdbc/execute! db (sql/format
                          {:insert-into :identity.user
                           :columns     [:github_user_id :profile_image_png_base64]
                           :values      [[(str github-user-id) (duke/duke->png-base64 (Duke. (Seed. github-user-id)))]]
                           :on-conflict []
                           :do-nothing  true}))
      (let [{:user/keys [id]} (jdbc/execute-one! db (sql/format
                                                      {:select [:id]
                                                       :from   :identity.user
                                                       :where  [:= :github_user_id (str github-user-id)]}))]
        (-> (response/redirect "/")
            (assoc :session (-> (:session request)
                                (dissoc ::oauth2/access-tokens)
                                (assoc :user_id id))))))))

(defn get-discord-landing-handler
  [{:system/keys [db]} request]
  (let [{:keys [token]} (:discord (::oauth2/access-tokens (:session request)))]
    (let [response (clj-http-client/get "https://discord.com/api/oauth2/@me"
                                        {:headers {"Authorization" (str "Bearer " token)}})
          discord-user-info (get (cheshire/parse-string (:body response)) "user")
          discord-user-id (get discord-user-info "id")
          avatar-base64   (-> (Base64/getEncoder)
                              (Base64$Encoder/.encodeToString
                                (:body (clj-http.client/get (str "https://cdn.discordapp.com/avatars/"
                                                                 discord-user-id
                                                                 "/"
                                                                 (get discord-user-info "avatar")
                                                                 ".png?size=32")
                                                            {:as :byte-array}))))]
      (jdbc/execute! db (sql/format
                          {:insert-into :identity.user
                           :columns     [:discord_user_id :profile_image_png_base64]
                           :values      [[(str discord-user-id) avatar-base64]]
                           :on-conflict []
                           :do-nothing  true}))
      (let [{:user/keys [id]} (jdbc/execute-one! db (sql/format
                                                      {:select [:id]
                                                       :from   :identity.user
                                                       :where  [:= :discord_user_id (str discord-user-id)]}))]
        (-> (response/redirect "/")
            (assoc :session (-> (:session request)
                                (dissoc ::oauth2/access-tokens)
                                (assoc :user_id id))))))))

(defn get-logout-handler
  [_ _]
  (-> (response/redirect "/")
      (assoc :session nil)))

(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body [:div
           [:div {:class (classes ["flex"
                                   "h-screen"])}
            [:aside {:class (classes ["w-60 border-r bg-white flex flex-col"])}
             [:nav {:class (classes ["flex-1 px-3 space-y-1"])}
              [:a {:class (classes ["flex items-center gap-3 rounded-lg px-4 py-2 text-gray-700 hover:bg-gray-100"])}
               "Ask"]
              [:a {:class (classes ["flex items-center gap-3 rounded-lg px-4 py-2 text-gray-700 hover:bg-gray-100"])}
               "Answer"]
              [:a {:class (classes ["flex items-center gap-3 rounded-lg px-4 py-2 text-gray-700 hover:bg-gray-100"])}
               "Search"]
              [:a {:class (classes ["flex items-center gap-3 rounded-lg px-4 py-2 text-gray-700 hover:bg-gray-100"])}
               "Chat"]]
             #_[:nav {:class (classes ["flex-1 px-3 space-y-1"])}]]

            [:main {:class (classes ["flex-1 overflow-y-auto"])}

             [:div {:class "mt-10 flex flex-row items-center justify-center gap-x-6 gap-y-6"}
              (when-not (:user request)
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
                                      "focus-visible:outline-black"])} "Login with GitHub"])
              (when-not (:user request)
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
                                      "focus-visible:outline-black"])} "Login with Discord"])
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
               (let [user-info (jsonquery/execute-one! db {:select [:id :profile_image_png_base64]
                                                           :from   :identity.user
                                                           :where [:= :id (:user/id user)]})]
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
                     :style  "image-rendering: pixelated"}]]]))]]]))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]
    ["/logout" {:get (partial #'get-logout-handler system)}]
    (let [discord-launch-uri "/oauth2/discord"
          discord-redirect-uri "/oauth2/discord/callback"
          discord-landing-uri "/oauth2/discord/landing"]
      ["" {:middleware [#(wrap-oauth2 % {:discord
                                         {:authorize-uri    "https://discord.com/oauth2/authorize"
                                          :access-token-uri "https://discord.com/api/oauth2/token"
                                          :client-id        (System/getenv "DISCORD_CLIENT_ID")
                                          :client-secret    (System/getenv "DISCORD_CLIENT_SECRET")
                                          :scopes           ["identify" "email"]
                                          :launch-uri       discord-launch-uri
                                          :redirect-uri     discord-redirect-uri
                                          :landing-uri      discord-landing-uri}})]}
       [[discord-launch-uri {:get #'dummy-route-handler}]
        [discord-redirect-uri {:get #'dummy-route-handler}]
        [discord-landing-uri {:get (partial #'get-discord-landing-handler system)}]]])


    (let [github-launch-uri "/oauth2/github"
          github-redirect-uri "/oauth2/github/callback"
          github-landing-uri "/oauth2/github/landing"]
      ["" {:middleware [#(wrap-oauth2 % {:github
                                         {:authorize-uri    "https://github.com/login/oauth/authorize"
                                          :access-token-uri "https://github.com/login/oauth/access_token"
                                          :client-id        (System/getenv "GITHUB_CLIENT_ID")
                                          :client-secret    (System/getenv "GITHUB_CLIENT_SECRET")
                                          :scopes           ["user:email"]
                                          :launch-uri       github-launch-uri
                                          :redirect-uri     github-redirect-uri
                                          :landing-uri      github-landing-uri}})]}
       [[github-launch-uri {:get #'dummy-route-handler}]
        [github-redirect-uri {:get #'dummy-route-handler}]
        [github-landing-uri {:get (partial #'get-github-landing-handler system)}]]])]])




(defn get-did
  [atproto-handle]
  (let [dir (InitialDirContext.
              (doto (Hashtable.)
                (.put "java.naming.factory.initial",
                      "com.sun.jndi.dns.DnsContextFactory")))
        attrs (^[String String/1]
                DirContext/.getAttributes
                dir
                (str "_atproto." atproto-handle)
                (into-array String ["TXT"]))
        txt (Attributes/.get attrs "TXT")
        e (Attribute/.getAll txt)]
    (loop []
      (when (NamingEnumeration/.hasMore e)
        (let [value (NamingEnumeration/.next e)]
          (if (string/starts-with? value "did=")
            (string/replace-first value "did=" "")
            (recur)))))))

(defn resolve-did
  [did]
  (let [info (cheshire/parse-string-strict
               (slurp (str "https://plc.directory/" did)))
        service-endpoint (get-in info ["service" 0 "serviceEndpoint"])
        service-info (cheshire/parse-string-strict
                       (slurp (str service-endpoint "/.well-known/oauth-protected-resource")))
        authorization-server (get-in service-info ["authorization_servers" 0])
        authorization-server-description (cheshire/parse-string-strict
                                           (slurp (str authorization-server
                                                       "/.well-known/oauth-authorization-server")))]
    authorization-server-description))