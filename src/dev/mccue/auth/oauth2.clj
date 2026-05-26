(ns dev.mccue.auth.oauth2
  {:vendored "https://github.com/weavejester/ring-oauth2/blob/551475ee3f795a3e5e6942a68e40c64ad18f3c8c/src/ring/middleware/oauth2.clj"}
  (:require [clj-http.client :as http]
            [ring.util.request :as req]
            [ring.util.response :as resp])
  (:import (com.nimbusds.oauth2.sdk AuthorizationRequest$Builder ResponseType ResponseType$Value Scope)
           (com.nimbusds.oauth2.sdk.id ClientID State)
           (com.nimbusds.oauth2.sdk.pkce CodeChallengeMethod CodeVerifier)
           (java.net URI)
           (java.time Instant)
           (java.util Collection Date)))

(defn- redirect-uri [profile request]
  (-> (req/request-url request)
      (java.net.URI/create)
      (.resolve (:redirect-uri profile))))

(defn- scopes [profile]
  (^[Collection] Scope/parse (map name (:scopes profile))))


(defn- authorize-uri [profile request state verifier]
  (let [client-id (^[String] ClientID. (:client-id profile))
        request-builder (AuthorizationRequest$Builder.
                          (ResponseType. (into-array ResponseType$Value
                                                     [ResponseType$Value/CODE]))
                          client-id)]
    (-> request-builder
        (.state state)
        (.scope (scopes profile))
        (.redirectionURI (redirect-uri profile request))
        (.endpointURI (URI. (:authorize-uri profile))))
    (when (:pkce? profile)
      (^[CodeVerifier CodeChallengeMethod] .codeChallenge request-builder verifier CodeChallengeMethod/S256))
    (.toURI (.build request-builder))))

(defn make-launch-handler [{:keys [pkce?] :as profile}]
  (fn handler
    ([{:keys [session] :or {session {}} :as request}]
     (let [state    (State.)
           verifier (when pkce? (CodeVerifier.))
           session' (-> session
                        (assoc ::state (str state))
                        (cond-> pkce? (assoc ::code-verifier verifier)))]
       (-> (resp/redirect (str (authorize-uri profile request state verifier)))
           (assoc :session session'))))))

(defn- state-matches? [request]
  (= (get-in request [:session ::state])
     (get-in request [:query-params "state"])))

(defn- coerce-to-int [n]
  (if (string? n)
    (Integer/parseInt n)
    n))

(defn- seconds-from-now-to-date [secs]
  (-> (Instant/now) (.plusSeconds secs) (Date/from)))

(defn- format-access-token
  [{{:keys [access_token expires_in refresh_token id_token] :as body} :body}]
  (-> {:token access_token
       :extra-data (dissoc body
                           :access_token :expires_in
                           :refresh_token :id_token)}
      (cond-> expires_in (assoc :expires (-> (coerce-to-int expires_in)
                                             (seconds-from-now-to-date)))
              refresh_token (assoc :refresh-token refresh_token)
              id_token (assoc :id-token id_token))))

(defn- get-authorization-code [request]
  (get-in request [:query-params "code"]))

(defn- get-code-verifier [request]
  (get-in request [:session ::code-verifier]))

(defn- request-params [{:keys [pkce?] :as profile} request]
  (-> {:grant_type    "authorization_code"
       :code          (get-authorization-code request)
       :redirect_uri  (redirect-uri profile request)}
      (cond-> pkce? (assoc :code_verifier (get-code-verifier request)))))

(defn- add-header-credentials [opts id secret]
  (assoc opts :basic-auth [id secret]))

(defn- add-form-credentials [opts id secret]
  (assoc opts :form-params (-> (:form-params opts)
                               (merge {:client_id     id
                                       :client_secret secret}))))

(defn- access-token-http-options
  [{:keys [access-token-uri client-id client-secret basic-auth?]
    :or   {basic-auth? false} :as profile}
   request]
  (let [opts {:method      :post
              :url         access-token-uri
              :accept      :json
              :as          :json
              :form-params (request-params profile request)}]
    (if basic-auth?
      (add-header-credentials opts client-id client-secret)
      (add-form-credentials   opts client-id client-secret))))

(defn- get-access-token
  ([profile request]
   (-> (http/request (access-token-http-options profile request))
       (format-access-token))))

(defn state-mismatch-handler
  ([_]
   {:status  400
    :headers {"Content-Type" "text/plain; charset=utf-8"}
    :body    "OAuth2 error: state mismatch"}))

(defn no-auth-code-handler
  ([_]
   {:status  400
    :headers {"Content-Type" "text/plain; charset=utf-8"}
    :body    "OAuth2 error: no authorization code"}))

(defn- redirect-response [{:keys [id landing-uri]} session access-token]
  (-> (resp/redirect landing-uri)
      (assoc :session (-> session
                          (assoc-in [::access-tokens id] access-token)
                          (dissoc ::state ::code-verifier)))))

(defn make-redirect-handler
  [{:keys [state-mismatch-handler no-auth-code-handler]
    :or   {state-mismatch-handler state-mismatch-handler
           no-auth-code-handler   no-auth-code-handler}
    :as profile}]
  (fn
    ([{:keys [session] :or {session {}} :as request}]
     (cond
       (not (state-matches? request))
       (state-mismatch-handler request)

       (nil? (get-authorization-code request))
       (no-auth-code-handler request)

       :else
       (let [access-token (get-access-token profile request)]
         (redirect-response profile session access-token))))))

(defn- assoc-access-tokens [request]
  (if-let [tokens (-> request :session ::access-tokens)]
    (assoc request :oauth2/access-tokens tokens)
    request))

(defn- parse-redirect-url [{:keys [redirect-uri]}]
  (.getPath (java.net.URI. redirect-uri)))

(defn- valid-profile? [{:keys [client-id client-secret]}]
  (and (some? client-id) (some? client-secret)))

(defn wrap-oauth2 [handler profiles]
  {:pre [(every? valid-profile? (vals profiles))]}
  (let [profiles  (for [[k v] profiles] (assoc v :id k))
        launches  (into {} (map (juxt :launch-uri identity)) profiles)
        redirects (into {} (map (juxt parse-redirect-url identity)) profiles)]
    (fn
      ([{:keys [uri] :as request}]
       (if-let [profile (launches uri)]
         ((make-launch-handler profile) request)
         (if-let [profile (redirects uri)]
           ((:redirect-handler profile (make-redirect-handler profile)) request)
           (handler (assoc-access-tokens request))))))))

(defn ->reitit-routes
  [profiles]
  {:pre [(every? valid-profile? (vals profiles))]}
  (let [profiles  (for [[k v] profiles] (assoc v :id k))
        launches  (into {} (map (juxt :launch-uri make-launch-handler)) profiles)
        redirects (into {} (map (juxt parse-redirect-url make-redirect-handler)) profiles)]
    (vec (concat launches redirects))))