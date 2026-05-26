(ns dev.mccue.auth.routes
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as clj-http-client]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.auth.duke :as duke]
            [dev.mccue.auth.oauth2 :as oauth2]
            [dev.mccue.environment :as environment]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :refer [page-response]]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.request :as request]
            [ring.util.response :as response])

  (:import (com.nimbusds.oauth2.sdk AuthorizationErrorResponse AuthorizationRequest$Builder AuthorizationResponse AuthorizationSuccessResponse ErrorObject ResponseType Scope)
           (com.nimbusds.oauth2.sdk.id ClientID Identifier State)
           (dev.mccue.duke Duke Seed)
           (java.net URI)
           (java.util Base64 Base64$Encoder Hashtable)
           (javax.naming NameNotFoundException NamingEnumeration)
           (javax.naming.directory Attribute Attributes DirContext InitialDirContext)))


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
          body     (cheshire/parse-string (:body response))
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
                            :values      [[(:user/id (:user request))
                                           (str github-user-id)
                                           github-username
                                           avatar-base64]]
                            :returning [:id]
                            :on-conflict []
                            :do-nothing  true}))
      (-> (response/redirect "/")
          (assoc :session (-> (:session request)
                              (dissoc ::oauth2/access-tokens)))))))

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

(defn get-authorization-server-description
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

(defn atproto-client-doc
  [& {:keys [redirect_uris]}]
  {"application_type" "web",
   "client_name" "Example Browser App",
   "dpop_bound_access_tokens" true,
   "grant_types" ["authorization_code", "refresh_token"],
   "redirect_uris" redirect_uris,
   "response_types" ["code"],
   "scope" "atproto transition:generic",
   "token_endpoint_auth_method" "none"})


(defn get-atproto-client-metadata-json-handler
  [system request]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body (cheshire/generate-string
           {"client_id"   (str "https://" (get-in request [:headers "host"]))

            "application_type" "web",
            "client_name" "Example Browser App",
            "dpop_bound_access_tokens" true,
            "grant_types" ["authorization_code", "refresh_token"],
            "redirect_uris" ["http://127.0.0.1:8999/oauth2/atproto/callback"],
            "response_types" ["code"],
            "scope" "atproto transition:generic",
            "token_endpoint_auth_method" "none"})})

(defn get-atproto-launch-handler
  [system request]
  (let [handle                           (get-in request [:path-params :handle])
        did                              (get-did handle)
        authorization-server-description (get-authorization-server-description did)
        authorization_endpoint           (get authorization-server-description "authorization_endpoint")
        token_endpoint                   (get authorization-server-description "token_endpoint")
        launch-handler                   (oauth2/make-launch-handler
                                           {:authorize-uri    authorization_endpoint
                                            :access-token-uri token_endpoint
                                            :client-id        (if (environment/production?)
                                                                (str "https://" (get-in request [:headers "host"])
                                                                     "/oauth2/atproto/client-metadata.json")
                                                                (get
                                                                  (cheshire/parse-string
                                                                    (:body
                                                                      (clj-http.client/post
                                                                        "https://cimd-service.fly.dev/clients"
                                                                        {:body (cheshire/generate-string
                                                                                 (atproto-client-doc
                                                                                   :redirect-uris
                                                                                   ["http://127.0.0.1:8999/oauth2/atproto/callback"]))})))
                                                                  "client_id"))
                                            :client-secret    nil
                                            :scopes           ["atproto" "transition:generic"]
                                            :launch-uri       (:uri request)
                                            :redirect-uri     (str (if (environment/production?)
                                                                     (str "https://" (get-in request [:headers "host"]))
                                                                     "http://127.0.0.1:8999")
                                                                   "/oauth2/atproto/callback")

                                            :landing-uri      "/oauth2/atproto/landing"
                                            :pkce?            true})]
    (launch-handler request)))

(def github-launch-uri "/oauth2/github")
(def github-redirect-uri "/oauth2/github/callback")
(def github-landing-uri "/oauth2/github/landing")

(defn get-github-launch-handler
  [system request]
  (let [authorize-uri (URI. "https://github.com/login/oauth/authorize")
        client-id     (ClientID. (System/getenv "GITHUB_CLIENT_ID"))
        scope         (Scope/parse ["user:email"])
        redirect-uri  (URI. (-> (request/request-url request)
                                (URI/create)
                                (.resolve github-redirect-uri)
                                (str)))
        state         (State.)
        request       (-> (AuthorizationRequest$Builder.
                            (ResponseType. (into-array String ["code"]))
                            client-id)
                          (.state state)
                          (.scope scope)
                          (.redirectionURI redirect-uri)
                          (.endpointURI authorize-uri)

                          (.build))]
    (-> (response/redirect (str (.toURI request)))
        (assoc-in [:session ::state] (State/.getValue state)))))

(defn get-github-redirect-handler
  [{:system/keys [db]} request]
  (let [parsed-response (^[URI] AuthorizationResponse/parse (URI/create (str (:uri request)
                                                                             "?"
                                                                             (:query-string request))))
        session-state   (get-in request [:session ::state])]
    (if (not= session-state (str (AuthorizationResponse/.getState parsed-response)))
      (oauth2/state-mismatch-handler request)
      (if-not (AuthorizationResponse/.indicatesSuccess parsed-response)
        (let [error-object (-> (AuthorizationResponse/.toErrorResponse parsed-response)
                               (AuthorizationErrorResponse/.getErrorObject))]
          {:status  (ErrorObject/.getHTTPStatusCode error-object)
           :headers {"Content-Type" "text/plain; charset=utf-8"}
           :body    (ErrorObject/.getDescription error-object)})
        (let [success-response (AuthorizationResponse/.toSuccessResponse parsed-response)
              token             (str (AuthorizationSuccessResponse/.getAuthorizationCode success-response))]
          (let [response (clj-http-client/get "https://api.github.com/user"
                                              {:headers {"Authorization" (str "Bearer " token)}})
                body     (cheshire/parse-string (:body response))
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
                                  :values      [[(:user/id (:user request))
                                                 (str github-user-id)
                                                 github-username
                                                 avatar-base64]]
                                  :returning [:id]
                                  :on-conflict []
                                  :do-nothing  true}))
            (-> (response/redirect "/")
                (assoc :session (-> (:session request)
                                    (dissoc ::state))))))))))



; .state(state)
;    .redirectionURI(callback)
;    .endpointURI(authzEndpoint)
;    .build()
; URI authzEndpoint = new URI("https://c2id.com/authz");





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
                                   :landing-uri      discord-landing-uri}})
         [discord-landing-uri {:get (partial #'get-discord-landing-handler system)}])])

    [github-launch-uri {:get (partial #'get-github-launch-handler system)}]
    [github-redirect-uri {:get (partial #'get-github-redirect-handler system)}]
    #_(let [github-launch-uri "/oauth2/github"
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
                                     :landing-uri      github-landing-uri}})
           [github-landing-uri {:get (partial #'get-github-landing-handler system)}])])

    ["/oauth2/atproto/launch/:handle"
     {:get (partial #'get-atproto-launch-handler system)}]
    ["/oauth2/atproto/callback"]
    ["/oauth2/atproto/landing"]
    ["/oauth2/atproto/client-metadata.json"
     {:get (partial #'get-atproto-client-metadata-json-handler system)}]
    ["/not-oauth/atproto" {:get (partial #'get-atproto-handler system)
                           :post (partial #'post-atproto-handler system)}]]])


