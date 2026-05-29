(ns dev.mccue.auth.oauth2
  {:vendored "https://github.com/weavejester/ring-oauth2/blob/551475ee3f795a3e5e6942a68e40c64ad18f3c8c/src/ring/middleware/oauth2.clj"}
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [ring.util.request]
            [ring.util.request :as req]
            [ring.util.response :as resp])
  (:import (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk Curve ECKey JWK)
           (com.nimbusds.jose.jwk.gen ECKeyGenerator)
           (com.nimbusds.oauth2.sdk AccessTokenResponse AuthorizationCode AuthorizationCodeGrant AuthorizationErrorResponse
                                    AuthorizationRequest AuthorizationRequest$Builder
                                    AuthorizationResponse
                                    ErrorObject
                                    OAuth2Error PushedAuthorizationErrorResponse PushedAuthorizationRequest
                                    PushedAuthorizationResponse
                                    PushedAuthorizationSuccessResponse
                                    ResponseType
                                    ResponseType$Value
                                    Scope TokenErrorResponse TokenRequest$Builder TokenResponse)
           (com.nimbusds.oauth2.sdk.dpop DPoPProofFactory DefaultDPoPProofFactory)
           (com.nimbusds.oauth2.sdk.http HTTPRequest HTTPResponse)
           (com.nimbusds.oauth2.sdk.id ClientID State)
           (com.nimbusds.oauth2.sdk.pkce CodeChallengeMethod CodeVerifier)
           (com.nimbusds.oauth2.sdk.token AccessToken Tokens)
           (com.nimbusds.openid.connect.sdk Nonce)
           (java.net URI)
           (java.time Instant)
           (java.util Collection Date Map)))

(defn- redirect-uri [profile request]
  (-> (req/request-url request)
      (cond-> (:force-https profile)
              (string/replace-first #"http://" "https://"))
      (java.net.URI/create)
      (.resolve ^String (:redirect-uri profile))))

(defn- scopes [profile]
  (^[Collection] Scope/parse (map name (:scopes profile))))

(defn- authorize-request [profile request state verifier]
  (let [client-id (ClientID. ^String (:client-id profile))
        request-builder (AuthorizationRequest$Builder.
                          (ResponseType. ^ResponseType$Value/1
                                         (into-array ResponseType$Value
                                                     [ResponseType$Value/CODE]))
                          client-id)]
    (-> request-builder
        (.state state)
        (.scope (scopes profile))
        (.redirectionURI (redirect-uri profile request))
        (.endpointURI (URI. (:authorize-uri profile))))
    (when (:login-hint profile)
      (.customParameter request-builder "login_hint" (into-array String [(:login-hint profile)])))
    (when-let [mode (:prompt profile)]
      (.customParameter request-builder "prompt" (into-array String [mode])))
    (when (:pkce? profile)
      (.codeChallenge request-builder ^CodeVerifier verifier CodeChallengeMethod/S256))
    (.build request-builder)))

(defn- send-dpop-par-request
  [par-endpoint proof-factory auth-req nonce]
  (log/info "About to send PAR Request: " (AuthorizationRequest/.toParameters auth-req))
  (let [par-req         (PushedAuthorizationRequest. (URI. par-endpoint) auth-req)
        http-req        (-> par-req (.toHTTPRequest))
        _               (HTTPRequest/.setDPoP http-req
                                              (DPoPProofFactory/.createDPoPJWT
                                                proof-factory
                                                (-> (HTTPRequest/.getMethod http-req)
                                                    (Enum/.name))
                                                (HTTPRequest/.getURI http-req)
                                                nil
                                                nonce))
        http-res        (-> http-req (.send))
        par-res         (PushedAuthorizationResponse/parse http-res)
        {:keys [par-res
                nonce
                error-response]} (or (and (PushedAuthorizationResponse/.indicatesSuccess par-res)
                                          {:par-res par-res
                                           :nonce   nonce})
                                     (let [error-object (-> par-res
                                                            (PushedAuthorizationResponse/.toErrorResponse)
                                                            (PushedAuthorizationErrorResponse/.getErrorObject))]
                                       (if (and (nil? nonce)
                                                (= error-object OAuth2Error/USE_DPOP_NONCE))
                                         (send-dpop-par-request par-endpoint proof-factory auth-req
                                                                (or (some-> http-res
                                                                            (HTTPResponse/.getDPoPNonce))
                                                                    (Nonce.)))
                                         {:error-response
                                          {:status  (ErrorObject/.getHTTPStatusCode error-object)
                                           :headers {"Content-Type" "text/plain; charset=utf-8"}
                                           :body    (ErrorObject/.getDescription error-object)}})))]
    {:par-res        par-res
     :nonce          nonce
     :error-response error-response}))

(defn make-launch-handler [{:keys [pkce?] :as profile}]
  (fn handler
    ([{:keys [session] :or {session {}} :as request}]
     (let [state    (State.)
           verifier (when pkce? (CodeVerifier.))
           session' (-> session
                        (assoc ::state (str state))
                        (cond-> pkce? (assoc ::code-verifier (.getValue verifier))))
           auth-req (authorize-request profile request state verifier)]
       ;; If we are using PAR, we take this url, POST it,
       ;; and redirect to whatever url is returned.

       ;; Also, assume DPoP if we are doing PAR. IDK if this is best
       ;; for a generic client, but I made the rules here
       (if-let [par-endpoint (:pushed-authorization-request-endpoint profile)]
         (let [dpop-key                 (-> (ECKeyGenerator. Curve/P_256)
                                            (.keyID (str (random-uuid)))
                                            (.generate))
               ;; Proof Factory would be an awesome podcast name
               proof-factory            (DefaultDPoPProofFactory. dpop-key JWSAlgorithm/ES256)
               {:keys [par-res
                       nonce
                       error-response]} (send-dpop-par-request par-endpoint proof-factory auth-req nil)]
           (or
             error-response
             (-> (resp/redirect (str (URI. (:authorize-uri profile))
                                     "?client_id=" (:client-id profile)
                                     "&request_uri="
                                     (-> (PushedAuthorizationResponse/.toSuccessResponse par-res)
                                         (PushedAuthorizationSuccessResponse/.getRequestURI))))

                 (assoc :session (-> session'
                                     (assoc ::dpop {:key (str dpop-key)
                                                    :nonce (.getValue nonce)}))))))
         (-> (resp/redirect (str (.toURI auth-req)))
             (assoc :session session')))))))


(defn- state-matches? [request authorization-response]
  (= (some-> (get-in request [:session ::state])
             (^[String] State.))
     (AuthorizationResponse/.getState authorization-response)))

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
  (CodeVerifier. (get-in request [:session ::code-verifier])))

(defn- request-params [{:keys [pkce?] :as profile} request]
  (-> {:grant_type    "authorization_code"
       :code          (get-authorization-code request)
       :redirect_uri  (redirect-uri profile request)}
      (cond-> pkce? (assoc :code_verifier (.getValue (get-code-verifier request))))))

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

(defn- send-dpop-token-request
  [token-request proof-factory nonce]
  (let [http-req        (-> token-request (.toHTTPRequest))
        uri             (.getURI http-req)
        htu             (URI/create (str (.getScheme uri)
                                         "://"
                                         (.getAuthority uri)
                                         (.getPath uri)))
        _               (HTTPRequest/.setDPoP http-req
                                              (DPoPProofFactory/.createDPoPJWT
                                                proof-factory
                                                (-> (HTTPRequest/.getMethod http-req)
                                                    (Enum/.name))
                                                htu
                                                nil
                                                nonce))
        http-res        (-> http-req (.send))
        token-res       (TokenResponse/parse http-res)
        {:keys [token-res
                nonce
                error-response]} (or (and (TokenResponse/.indicatesSuccess token-res)
                                          {:token-res token-res
                                           :nonce   nonce})
                                     (let [error-object (-> token-res
                                                            (TokenResponse/.toErrorResponse)
                                                            (TokenErrorResponse/.getErrorObject))]
                                       (if (and (nil? nonce)
                                                (= error-object OAuth2Error/USE_DPOP_NONCE))
                                         (send-dpop-token-request
                                           token-request
                                           proof-factory
                                           (or (some-> http-res
                                                       (HTTPResponse/.getDPoPNonce))
                                               (Nonce.)))
                                         {:error-response
                                          {:status  (ErrorObject/.getHTTPStatusCode error-object)
                                           :headers {"Content-Type" "text/plain; charset=utf-8"}
                                           :body    (ErrorObject/.getDescription error-object)}})))]
    {:token-res      token-res
     :nonce          nonce
     :error-response error-response}))

(defn- get-access-token
  [profile request]
  (if-let [dpop-key (some-> (get-in request [:session ::dpop :key])
                            (cheshire/parse-string-strict)
                            (ECKey/parse))]

    (let [code          (AuthorizationCode. ^String (get-authorization-code request))
          redirect-uri  (redirect-uri profile request)
          verifier      (CodeVerifier. ^String (get-in request [:session ::code-verifier]))
          grant         (AuthorizationCodeGrant. code redirect-uri verifier)
          token-request (-> (TokenRequest$Builder. (URI. (:access-token-uri profile))
                                                   (ClientID. ^String (:client-id profile))
                                                   grant)
                            (.build))

          proof-factory (DefaultDPoPProofFactory. dpop-key JWSAlgorithm/ES256)
          {:keys [token-res
                  nonce
                  error-response]} (send-dpop-token-request token-request proof-factory nil)]
      (if error-response
        [nil error-response]
        (let [tokens (AccessTokenResponse/.getTokens token-res)]
          [(-> {:token         (str (Tokens/.getAccessToken tokens))
                :refresh-token (str (Tokens/.getRefreshToken tokens))
                :scopes        (str (AccessToken/.getScope (Tokens/.getAccessToken tokens)))
                :sub           (get (AccessTokenResponse/.getCustomParameters token-res) "sub")})
           nil])))
    [(-> (http/request (access-token-http-options profile request))
         (format-access-token))
     nil]))

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
                          (assoc-in [::access-tokens id] (merge access-token
                                                                {:dpop-private-key (get-in session [::dpop :key])}))
                          (dissoc ::state ::code-verifier ::dpop)))))

(defn make-redirect-handler
  [{:keys [state-mismatch-handler no-auth-code-handler]
    :or   {state-mismatch-handler state-mismatch-handler
           no-auth-code-handler   no-auth-code-handler}
    :as profile}]
  (fn
    [{:keys [session] :or {session {}} :as request}]
    (let [authorization-response (^[URI Map] AuthorizationResponse/parse
                                   (URI/create (:uri request))
                                   (update-vals (:query-params request)
                                                #(if (string? %) [%] %)))]
      (cond
        (not (state-matches? request authorization-response))
        (state-mismatch-handler request)

        (not (AuthorizationResponse/.indicatesSuccess authorization-response))
        (let [error-object (-> (AuthorizationResponse/.toErrorResponse authorization-response)
                               (AuthorizationErrorResponse/.getErrorObject))]
          {:status  (ErrorObject/.getHTTPStatusCode error-object)
           :headers {"Content-Type" "text/plain; charset=utf-8"}
           :body    (ErrorObject/.getDescription error-object)
           ::code  (ErrorObject/.getCode error-object)})

        (nil? (get-authorization-code request))
        (no-auth-code-handler request)

        :else
        (let [[access-token error-response] (get-access-token profile request)]
          (or error-response
              (redirect-response profile session access-token)))))))

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
      [{:keys [uri] :as request}]
      (if-let [profile (launches uri)]
        ((make-launch-handler profile) request)
        (if-let [profile (redirects uri)]
          ((:redirect-handler profile (make-redirect-handler profile)) request)
          (handler (assoc-access-tokens request)))))))

(defn ->reitit-routes
  [profiles]
  {:pre [(every? valid-profile? (vals profiles))]}
  (let [profiles  (for [[k v] profiles] (assoc v :id k))
        launches  (into {} (map (juxt :launch-uri make-launch-handler)) profiles)
        redirects (into {} (map (juxt parse-redirect-url make-redirect-handler)) profiles)]
    (vec (concat launches redirects))))