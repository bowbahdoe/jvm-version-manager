(ns dev.mccue.auth.routes
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as clj-http-client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.auth.duke :as duke]
            [dev.mccue.auth.oauth2 :as oauth2]
            [dev.mccue.environment :as environment]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :refer [page-response classes]]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response])
  (:import (dev.mccue.duke Duke Seed)
           (java.net URI)
           (java.time Instant)
           (java.util Base64 Base64$Encoder Hashtable)
           (javax.naming NameNotFoundException NamingEnumeration)
           (javax.naming.directory Attribute Attributes DirContext InitialDirContext)))

(defn get-github-landing-handler
  [{:system/keys [db]} request]
  (let [{:keys [token]} (:github (::oauth2/access-tokens (:session request)))]
    (let [response (clj-http-client/get "https://api.github.com/user"
                                  {:headers {"Authorization" (str "Bearer " token)}})
          body     (cheshire/parse-string-strict (:body response))
          github-user-id (get body "id")
          github-username (get body "login")
          avatar-base64   (-> (Base64/getEncoder)
                              (Base64$Encoder/.encodeToString
                                (:body (clj-http.client/get (str "https://github.com/"
                                                                 github-username
                                                                 ".png?size=32")
                                                            {:as :byte-array}))))]
      ;; TODO: Handle conflict when another user has the same github account linked.
      (jdbc/execute-one! db
                         (sql/format
                           {:insert-into :github.linked_account
                            :columns     [:user_id
                                          :github_user_id
                                          :github_username
                                          :github_profile_image_png_base64]
                            :values      [[(:user/id (:identity request))
                                           (str github-user-id)
                                           github-username
                                           avatar-base64]]
                            :returning [:id]
                            :on-conflict []
                            :do-nothing  true}))
      (-> (response/redirect "/profile")
          (assoc :session (-> (:session request)
                              (dissoc ::oauth2/access-tokens)))))))

(defn get-discord-landing-handler
  [{:system/keys [db]} request]
  (let [{:keys [token]} (:discord (::oauth2/access-tokens (:session request)))]
    (let [response (clj-http-client/get "https://discord.com/api/oauth2/@me"
                                        {:headers {"Authorization" (str "Bearer " token)}})
          discord-user-info (get (cheshire/parse-string-strict (:body response)) "user")
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
                           :values      [[(:user/id (:identity request))
                                          (str discord-user-id)
                                          discord-user-username
                                          avatar-base64]]
                           ;; TODO: Handle conflict when another user has the same discord account linked.
                           :on-conflict []
                           :do-nothing  true}))
      (-> (response/redirect "/profile")
          (assoc :session (-> (:session request)
                              (dissoc ::oauth2/access-tokens)))))))

(defn get-logout-handler
  [_ _]
  (-> (response/redirect "/")
      (assoc :session nil)))

(defn get-login-handler
  [system request]
  (page-response
    :title "Login"
    :body [:main {:class (classes ["flex"
                                   "flex-col"
                                   "h-full"
                                   "justify-center"])}
           [:div {:class (classes ["flex"
                                   "flex-col"
                                   "place-items-center"
                                   "h-full"])}
            [:div {:class (classes ["grow"])}]
            [:div {:class (classes ["h-2/5"
                                    "outline-3"
                                    "flex"
                                    "flex-col"
                                    "w-2/5"
                                    "place-items-center"
                                    "gap-5"])}
             [:p {:class (classes ["text-lg" "font-bold" "m-2"])}
              "Login"]
             [:p {:class (classes ["text-md"])}
              "Connect with your "
              [:a {:href "https://atmosphereaccount.com/"
                   :class (classes ["underline"])
                   :target "_blank"}
               "Atmosphere account"]]

             [:div {:class (classes ["flex" "flex-col" "gap-3"])}
              [:div {:class (classes ["flex" "flex-col" "gap-3" "outline-2" "outline-dashed" "p-2"])}
               [:form {:class (classes ["flex" "flex-col" "gap-3"])
                       :method "GET"
                       :action "/oauth2/atproto/launch"}
                [:label {:for "handle"
                         :class (classes ["text-center" ])} "Handle"]
                [:input {:class (classes ["outline-1" "p-2"])
                         :type "text"
                         :name "handle"
                         :placeholder "dril.bsky.social"
                         :id "handle"
                         :required true
                         :autocorrect "off"
                         :autocapitalize "off"
                         :spellcheck"false"}]
                [:input {:type "submit"
                         :class (classes ["outline-2" "cursor-pointer" "p-2"
                                          "hover:bg-blue-200"
                                          "focus-visible:bg-blue-200"
                                          "text-center"])
                         :value "Login"}]]]
              [:a {:href "#"
                   :class (classes ["flex" "flex-row" "s-2" "align-center"
                                    "outline-2" "cursor-pointer" "p-2"
                                    "hover:bg-red-200"
                                    "focus-visible:bg-blue-200"
                                    "text-center"])}
               [:img {:src "/bluesky.svg"
                      :class (classes ["size-[1em]" "self-center"])}]
               [:div {:class (classes "w-[1em]")}]
               "Login with Bluesky"]]
             (when (:flash request)
               [:p {:class (classes ["text-red-500"])}
                (:flash request)])]
            [:div {:class (classes ["grow"])}]]]))



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
  (try
    (let [info (cheshire/parse-string-strict
                (slurp (str "https://plc.directory/" did)))]
      (get-in info ["service" 0 "serviceEndpoint"]))
    (catch Exception _ nil)))

(defn resolve-service-info
  [service-endpoint]
  (try
    (cheshire/parse-string-strict
      (slurp (str service-endpoint "/.well-known/oauth-protected-resource")))
    (catch Exception _ nil)))

(defn resolve-authorization-server-endpoint
  [service-info]
  (get-in service-info ["authorization_servers" 0]))

(defn resolve-authorization-server-description
  [authorization-server-endpoint]
  (cheshire/parse-string-strict
    (slurp (str authorization-server-endpoint
                "/.well-known/oauth-authorization-server"))))

(def atproto-scopes
  "atproto repo:dev.mccue.module?action=create blob:application/java-archive account:email")

(defn atproto-client-doc
  [& {:keys [redirect-uris]}]
  {"application_type"           "web"
   "client_name"                "Example Browser App",
   "dpop_bound_access_tokens"   true,
   "grant_types"                ["authorization_code", "refresh_token"],
   "redirect_uris"              redirect-uris,
   "response_types"             ["code"],
   "scope"                      atproto-scopes,
   "token_endpoint_auth_method" "none"
   "client_uri"                 "https://jvm.mccue.dev"})

;;TODO: make client metadata route *just* /oauth-client-metadata.json
(defonce boot-time-str
         (str (-> (Instant/now)
                  (Instant/.getEpochSecond))))

(def production-client-id
  (str "https://jvm.mccue.dev/oauth2/atproto/client-metadata.json"
       "?serverboottime=" boot-time-str))

(defn get-atproto-client-metadata-json-handler
  [system request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string
           (merge {"client_id"   production-client-id}
                  (atproto-client-doc :redirect-uris [(if (environment/production?)
                                                         "https://jvm.mccue.dev/oauth2/atproto/callback"
                                                         "http://127.0.0.1:8999/oauth2/atproto/callback")])))})

(def atproto-client-id
  (delay
    (if (environment/production?)
      production-client-id
      (-> (clj-http.client/post
            "https://cimd-service.fly.dev/clients"
            {:body (cheshire/generate-string
                     (atproto-client-doc
                       :redirect-uris
                       ["http://127.0.0.1:8999/oauth2/atproto/callback"]))})
          (:body)
          (cheshire/parse-string-strict)
          (get "client_id")))))

(defn get-atproto-launch-handler
  [_ request]
  (let [handle (get-in request [:query-params "handle"])]
    (if-let [did (get-did handle)]
      (if-let [service-endpoint (resolve-service-endpoint did)]
        (if-let [service-info (resolve-service-info service-endpoint)]
          (let [authorization-server-endpoint    (resolve-authorization-server-endpoint service-info)
                authorization-server-description (resolve-authorization-server-description authorization-server-endpoint)
                authorization_endpoint           (get authorization-server-description "authorization_endpoint")
                token_endpoint                   (get authorization-server-description "token_endpoint")
                par_endpoint                     (get authorization-server-description "pushed_authorization_request_endpoint")
                launch-handler                   (oauth2/make-launch-handler
                                                   {:authorize-uri    authorization_endpoint
                                                    :access-token-uri token_endpoint
                                                    :client-id        @atproto-client-id
                                                    :scopes           [atproto-scopes]
                                                    :launch-uri       (:uri request)
                                                    :redirect-uri     (if (environment/production?)
                                                                        "https://jvm.mccue.dev/oauth2/atproto/callback"
                                                                        "http://127.0.0.1:8999/oauth2/atproto/callback")
                                                    :landing-uri      "/oauth2/atproto/landing"
                                                    :pkce?            true
                                                    :pushed-authorization-request-endpoint par_endpoint
                                                    :login-hint                            handle})]

            (-> (launch-handler request)
                (update :session assoc ::oauth2/atproto-service-endpoint service-endpoint)
                (update :session assoc ::oauth2/token-endpoint token_endpoint)
                (update :session assoc ::oauth2/atproto-did did)
                (update :session assoc ::oauth2/atproto-handle handle)))
          (-> (response/redirect "/login")
              (assoc :flash (str "Issue resolving service info for " handle " on " service-endpoint))))
        (-> (response/redirect "/login")
            (assoc :flash (str "Issue resolving service endpoint for " handle))))
      (-> (response/redirect "/login")
          (assoc :flash (str "No Atmosphere account found for " handle))))))

(defn get-atproto-callback-handler
  [system request]
  (let [token_endpoint   (get-in request [:session ::oauth2/token-endpoint])
        redirect-handler (oauth2/make-redirect-handler
                           {:id               :atproto
                            :access-token-uri token_endpoint
                            :client-id        @atproto-client-id
                            :redirect-uri     (if (environment/production?)
                                                "https://jvm.mccue.dev/oauth2/atproto/callback"
                                                "http://127.0.0.1:8999/oauth2/atproto/callback")
                            :landing-uri      "/oauth2/atproto/landing"
                            :pkce?            true})
        response         (redirect-handler request)]
    (-> (if (= (::oauth2/code response) "access_denied")
          (response/redirect "/login")
          response)
        (update :session dissoc ::oauth2/token-endpoint))))

(defn get-atproto-landing-handler
  [{:system/keys [db]} request]
  (let [handle                  (get-in request [:session ::oauth2/atproto-handle])
        did                     (get-in request [:session ::oauth2/atproto-did])
        service-endpoint        (get-in request [:session ::oauth2/atproto-service-endpoint])
        access-tokens           (get-in request [:session ::oauth2/access-tokens :atproto])
        {:keys [token
                refresh-token]} access-tokens]
    (jdbc/with-transaction [t db]
      (jdbc/execute! t (sql/format
                         {:insert-into :atproto.access_credential
                          :columns [:access_token :refresh_token :service_endpoint :did]
                          :values [[token refresh-token service-endpoint did]]
                          :on-conflict [:did]
                          :do-update-set [:access_token :refresh_token :service_endpoint]}))
      (jdbc/execute! t (sql/format
                         {:insert-into :identity.user
                          :columns     [:atproto_did :atproto_handle :profile_image_png_base64]
                          :values      [[did handle (duke/duke->png-base64 (Duke. (Seed. (hash did))))]]
                          :on-conflict [:atproto_did]
                          :do-update-set [:atproto_handle]})))
    (let [{:user/keys [id]} (jdbc/execute-one! db (sql/format
                                                    {:select [:id]
                                                     :from   :identity.user
                                                     :where  [:= :atproto_did did]}))]
      (-> (response/redirect "/")
          (assoc :session (-> (:session request)
                              (assoc :user_id id)
                              (dissoc ::oauth2/atproto-handle)
                              (dissoc ::oauth2/atproto-did)
                              (dissoc ::oauth2/atproto-service-endpoint)
                              (dissoc ::oauth2/access-tokens)))))))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/logout" {:get (partial #'get-logout-handler system)}]
    (let [discord-launch-uri "/oauth2/discord"
          discord-redirect-uri "/oauth2/discord/callback"
          discord-landing-uri "/oauth2/discord/landing"]
      ["" {:middleware [(middleware/require-authenticated-user-middleware system)]}
       (conj
         (oauth2/->reitit-routes {:discord
                                  {:authorize-uri    "https://discord.com/oauth2/authorize"
                                   :access-token-uri "https://discord.com/api/oauth2/token"
                                   :client-id        (System/getenv "DISCORD_CLIENT_ID")
                                   :client-secret    (System/getenv "DISCORD_CLIENT_SECRET")
                                   :scopes           ["identify" "email"]
                                   :launch-uri       discord-launch-uri
                                   :redirect-uri     discord-redirect-uri
                                   :landing-uri      discord-landing-uri
                                   :force-https      (environment/production?)}})
         [discord-landing-uri {:get (partial #'get-discord-landing-handler system)}])])

    (let [github-launch-uri "/oauth2/github"
          github-redirect-uri "/oauth2/github/callback"
          github-landing-uri "/oauth2/github/landing"]
      ["" {:middleware [(middleware/require-authenticated-user-middleware system)]}
       (conj
         (oauth2/->reitit-routes {:github
                                  {:authorize-uri    "https://github.com/login/oauth/authorize"
                                   :access-token-uri "https://github.com/login/oauth/access_token"
                                   :client-id        (System/getenv "GITHUB_CLIENT_ID")
                                   :client-secret    (System/getenv "GITHUB_CLIENT_SECRET")
                                   :scopes           ["user:email"]
                                   :launch-uri       github-launch-uri
                                   :redirect-uri     github-redirect-uri
                                   :landing-uri      github-landing-uri
                                   :force-https      (environment/production?)}})
         [github-landing-uri {:get (partial #'get-github-landing-handler system)}])])

    ["/oauth2/atproto/launch"
     {:get (partial #'get-atproto-launch-handler system)}]
    ["/oauth2/atproto/callback"
     {:get (partial #'get-atproto-callback-handler system)}]
    ["/oauth2/atproto/landing"
     {:get (partial #'get-atproto-landing-handler system)}]
    ["/oauth2/atproto/client-metadata.json"
     {:get (partial #'get-atproto-client-metadata-json-handler system)}]
    ["/login"
     {:get (partial #'get-login-handler system)}]]])


