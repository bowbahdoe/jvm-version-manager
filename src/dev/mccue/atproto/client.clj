(ns dev.mccue.atproto.client
  (:require [cheshire.core :as cheshire]
            [next.jdbc :as jdbc])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk ECKey)
           (com.nimbusds.jwt SignedJWT)
           (com.nimbusds.oauth2.sdk.dpop DPoPProofFactory DefaultDPoPProofFactory)
           (com.nimbusds.oauth2.sdk.http HTTPRequest)
           (com.nimbusds.oauth2.sdk.token AccessToken AccessTokenType)
           (com.nimbusds.openid.connect.sdk Nonce)
           (java.net URI)))
(comment
  (defn from-credentials
    [credential])



  (def cred (some-> (jdbc/execute-one!
                      (user/db)
                      ["SELECT * FROM atproto.access_credential WHERE did=?"
                       "did:plc:2oip3ubsbe2pc7tmbnwsm3i7"])
                    (update-keys name)
                    (update-keys keyword)))

  (def dpop-key (ECKey/parse (:dpop_private_key cred)))

  (def request-path "/xrpc/com.atproto.repo.putRecord")
  (def request-uri (str (:service_endpoint cred)
                        request-path))

  (def proof-factory (DefaultDPoPProofFactory. dpop-key JWSAlgorithm/ES256))
  (def access-token  (AccessToken/parse (str "DPoP " (:access_token cred)) AccessTokenType/DPOP))
  (def dpop-jwt
    (fn [nonce]
      (DPoPProofFactory/.createDPoPJWT
        proof-factory
        "POST"
        (URI. request-uri) ;; htu
        access-token
        (when nonce
          (Nonce/parse nonce)))))

  (def query-string (str "?repo=" (:did cred)
                         "&rkey=" "self"
                         "&collection=" "app.bsky.actor.profile"))

  (defn put-record!
    [& {:keys [collection rkey record]
        :or {rkey "self"}}]
    (let [headers {"DPoP" (SignedJWT/.serialize (dpop-jwt nil))
                   "Authorization" (str (AccessToken/.getType access-token) " " access-token)
                   "Content-Type" "application/json"}]
      (try
        (clj-http.client/post
          full-request-uri
          {:headers headers
           :body    (cheshire/generate-string
                      {:repo       (:did cred)
                       :rkey       rkey
                       :collection collection
                       :record     (assoc record "$type" collection)})})
        (catch ExceptionInfo e
          (let [error (get (cheshire/parse-string-strict (:body (ex-data e)))
                           "error")]
            (if (= error "use_dpop_nonce")
              (clj-http.client/post
                full-request-uri
                {:headers (assoc headers
                            "DPoP" (SignedJWT/.serialize (dpop-jwt (get-in (ex-data e) [:headers "dpop-nonce"]))))
                 :body    (cheshire/generate-string
                            {:repo       (:did cred)
                             :rkey       rkey
                             :collection collection
                             :record     (assoc record "$type" collection)})})
              (clojure.pprint/pprint (ex-data e))))))))
  (put-record! :collection "dev.mccue.module"
               :record {"createdAt"   "2026-05-28T22:49:10.863Z",
                        "name" "javad.base"}))


(defn send!
  [action method params])
