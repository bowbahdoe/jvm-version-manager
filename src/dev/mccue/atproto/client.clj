(ns dev.mccue.atproto.client
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk ECKey)
           (com.nimbusds.jwt SignedJWT)
           (com.nimbusds.oauth2.sdk.dpop DPoPProofFactory DefaultDPoPProofFactory)
           (com.nimbusds.oauth2.sdk.token AccessToken AccessTokenType)
           (com.nimbusds.openid.connect.sdk Nonce)
           (java.net URI)))

(defn refetch-credentials
  [db user-id]
  (jdbc/execute-one!
    db
    (sql/format
      {:select [:scopes
                :access_token
                :refresh_token
                :service_endpoint
                :did
                :dpop_private_key]
       :from :identity.user
       :left-join [:atproto.access_credential
                   [:= :atproto.access_credential.did :identity.user.atproto_did]]
       :where [:= :identity.user.id (if (string? user-id)
                                      (parse-uuid user-id)
                                      user-id)]})))

(defn refresh-credentials!
  [db user-id]
  'TODO)

(defn for-user
  [db user-id]
  (let [credentials (atom (refetch-credentials db user-id))]
    {::refetch-credentials refetch-credentials
     ::credentials-atom    credentials}))

(defn com-atproto-repo-putRecord
  [{::keys [credentials-atom]} & {:keys [collection record rkey]}]
  (let [credentials @credentials-atom
        dpop-key (^[String] ECKey/parse (:access_credential/dpop_private_key credentials))
        proof-factory (DefaultDPoPProofFactory. dpop-key JWSAlgorithm/ES256)
        request-path "/xrpc/com.atproto.repo.putRecord"
        request-uri (str (:access_credential/service_endpoint credentials) request-path)
        access-token  (AccessToken/parse (str "DPoP " (:access_credential/access_token credentials)) AccessTokenType/DPOP)
        dpop-jwt (fn [nonce]
                   (DPoPProofFactory/.createDPoPJWT
                     proof-factory
                     "POST"
                     (URI. request-uri) ;; htu
                     access-token
                     (when nonce
                       (Nonce/parse nonce))))
        headers {"DPoP" (SignedJWT/.serialize (dpop-jwt nil))
                 "Authorization" (str (AccessToken/.getType access-token) " " access-token)
                 "Content-Type" "application/json"}]
    (try
      (http/post
        request-uri
        {:headers headers
         :body    (cheshire/generate-string
                    (merge
                      {:repo       (:access_credential/did credentials)
                       :rkey       rkey
                       :collection collection
                       :record     (assoc record "$type" collection)}))})
      (catch ExceptionInfo e
        (let [error (some-> (:body (ex-data e))
                            (cheshire/parse-string-strict)
                            (get "error"))]
          (cond
            (= error "use_dpop_nonce")
            (clj-http.client/post
              request-uri
              {:headers (assoc headers
                          "DPoP" (SignedJWT/.serialize (dpop-jwt (get-in (ex-data e) [:headers "dpop-nonce"]))))
               :body    (cheshire/generate-string
                          {:repo       (:access_credential/did credentials)
                           :rkey       rkey
                           :collection collection
                           :record     (assoc record "$type" collection)})})
            (= error "invalid_token")
            (throw e)

            :else
            (throw e)))))))

(comment
  (def client (for-user
                (user/db)
                "019e70c9-7d6a-72cb-ae30-c44ffe64f6a8"))
  (com-atproto-repo-putRecord client
                              :collection "dev.mccue.jvm.module"
                              :record {:creat "f"}))
(comment
  (defn from-credentials
    [credential])



  (def cred (some-> (jdbc/execute-one!
                      (user/db)
                      ["SELECT * FROM atproto.access_credential WHERE did=?"
                       "did:plc:2oip3ubsbe2pc7tmbnwsm3i7"])
                    (update-keys name)
                    (update-keys keyword))))


(defn send!
  [action method params])
