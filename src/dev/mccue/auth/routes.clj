(ns dev.mccue.auth.routes
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.auth.duke :as duke]
            [dev.mccue.environment :as environment]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes page-response]]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [dev.mccue.auth.oauth2 :as oauth2 :refer [wrap-oauth2]]
            [clj-http.client :as clj-http-client]
            [ring.util.response :as response]
            [ring.util.anti-forgery :as anti-forgery])

  (:import (dev.mccue.duke Duke Seed)
           (java.net URI)
           (java.util Base64 Base64$Encoder Hashtable)
           (javax.naming NameNotFoundException NamingEnumeration)
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
          discord-user-username (get discord-user-info "username")
          avatar-base64   (-> (Base64/getEncoder)
                              (Base64$Encoder/.encodeToString
                                (:body (clj-http.client/get (str "https://cdn.discordapp.com/avatars/"
                                                                 discord-user-id
                                                                 "/"
                                                                 (get discord-user-info "avatar")
                                                                 ".png?size=32")
                                                            {:as :byte-array}))))]
      (jdbc/execute! db (sql/format
                          {:insert-into :discord.linked_account
                           :columns     [:user_id
                                         :discord_user_id
                                         :discord_username
                                         :discord_profile_image_png_base64]
                           :values      [[(:user/id (:user request))
                                          (str discord-user-id)
                                          discord-user-username
                                          avatar-base64]]
                           ;; TODO: Handle conflict when another user has the same discord account linked.
                           :on-conflict []
                           :do-nothing  true}))
      (-> (response/redirect "/")
          (assoc :session (-> (:session request)
                              (dissoc ::oauth2/access-tokens)))))))

(defn get-logout-handler
  [_ _]
  (-> (response/redirect "/")
      (assoc :session nil)))


(defn get-did-dns
  [atproto-handle]
  (try
    (let [dir (InitialDirContext.
                (doto (Hashtable.)
                  (.put "java.naming.factory.initial",
                        "com.sun.jndi.dns.DnsContextFactory")))
          attrs (^[String String/1]
                  DirContext/.getAttributes
                  dir
                  (str "_atproto." atproto-handle)
                  (into-array String ["TXT"]))]

      (when-let [txt (Attributes/.get attrs "TXT")]
        (let [e (Attribute/.getAll txt)]
          (loop []
            (when (NamingEnumeration/.hasMore e)
              (let [value (NamingEnumeration/.next e)]
                (if (string/starts-with? value "did=")
                  (string/replace-first value "did=" "")
                  (recur))))))))
    (catch NameNotFoundException _
      nil)))

(defn get-did-https
  [atproto-handle]
  (try (slurp (str "https://" atproto-handle "/.well-known/atproto-did"))
       (catch Exception _ nil)))

(defn get-did
  [atproto-handle]
  (or (get-did-dns atproto-handle)
      (get-did-https atproto-handle)))

(defn resolve-service-endpoint
  [did]
  (let [info (cheshire/parse-string-strict
              (slurp (str "https://plc.directory/" did)))]
    (get-in info ["service" 0 "serviceEndpoint"])))

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

(defn get-atproto-handler
  [system request]
  (page-response
    :body [:form {:action "/not-oauth/atproto"
                  :method "POST"}
           (hiccup/raw (anti-forgery/anti-forgery-field))
           [:label {:for "handle"} "Handle"]
           [:input {:type "text" :id "handle" :name "handle"}]
           [:label {:for "password"} "Password"]
           [:input {:type "password" :id "password" :name "password"}]
           [:input {:type "submit"}]]))

(defn post-atproto-handler
  [{:system/keys [db]} request]
  (let [{:strs [handle password]} (:form-params request)
        did                       (get-did handle)
        service-endpoint          (resolve-service-endpoint did)
        create-session-endpoint   (str (.resolve (URI/create service-endpoint)
                                                 "/xrpc/com.atproto.server.createSession/"))]
    (let [{:strs [did]}  (cheshire/parse-string
                           (:body (clj-http-client/post create-session-endpoint
                                                        {:body (cheshire/generate-string
                                                                 {:identifier handle
                                                                  :password   password})
                                                         :headers {"Content-Type" "application/json"}})))]
      (jdbc/execute! db (sql/format
                          {:insert-into :identity.user
                           :columns     [:atproto_did :profile_image_png_base64]
                           :values      [[did (duke/duke->png-base64 (Duke. (Seed. (hash did))))]]
                           :on-conflict []
                           :do-nothing  true}))
      (let [{:user/keys [id]} (jdbc/execute-one! db (sql/format
                                                      {:select [:id]
                                                       :from   :identity.user
                                                       :where  [:= :atproto_did did]}))]
        (-> (response/redirect "/")
            (assoc :session (-> (:session request)
                                (assoc :user_id id))))))))


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
               (let [user-info (jsonquery/execute-one! db {:select [:id :profile_image_png_base64
                                                                    [:discord_linked_accounts
                                                                     {:select [:discord_username
                                                                               :discord_profile_image_png_base64]
                                                                      :from :discord.linked_account
                                                                      :join-on [:id :user_id]}]]
                                                           :from   :identity.user
                                                           :where [:= :id (:user/id user)]})]
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
                   (for [account (:discord_linked_accounts user-info)]
                     [:div
                      {:class (page-helpers/classes ["flex"
                                                     "flex-col"
                                                     "items-center"
                                                     "p-7"
                                                     "rounded-2xl"])}
                      [:div
                       [:p (:discord_username account)]
                       [:img
                        {:class  (classes ["size-48"
                                           "rounded-md"
                                           "outline-4"
                                           "outline-offset-2"
                                           "outline-black"])
                         :src    (str "data:image/png;base64, " (:discord_profile_image_png_base64 account))
                         :width  64
                         :height 64
                         :style  "image-rendering: pixelated"}]]]))))]]]))



(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]
    ["/logout" {:get (partial #'get-logout-handler system)}]
    (let [discord-launch-uri "/oauth2/discord"
          discord-redirect-uri "/oauth2/discord/callback"
          discord-landing-uri "/oauth2/discord/landing"]
      ["" {:middleware [(middleware/require-authenticated-user-middleware system)
                        #(wrap-oauth2 % {:discord
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
      ["" {:middleware [(middleware/require-authenticated-user-middleware system)
                        #(wrap-oauth2 % {:github
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
        [github-landing-uri {:get (partial #'get-github-landing-handler system)}]]])

    ["/not-oauth/atproto" {:get (partial #'get-atproto-handler system)
                           :post (partial #'post-atproto-handler system)}]]])


