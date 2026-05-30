(ns dev.mccue.atproto.client
  (:require [cheshire.core :as json]
            [cheshire.core :as cheshire]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.repository.module-info :as mi]
            [camel-snake-kebab.core :as csk]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk ECKey)
           (com.nimbusds.jwt SignedJWT)
           (com.nimbusds.oauth2.sdk.dpop DPoPProofFactory DefaultDPoPProofFactory)
           (com.nimbusds.oauth2.sdk.token AccessToken AccessTokenType)
           (com.nimbusds.openid.connect.sdk Nonce)
           (java.net URI)
           (java.time OffsetDateTime)))

(defn refetch-credentials
  [db user-id]
  (jdbc/execute-one!
    db
    (sql/format
      {:select    [:scopes
                   :access_token
                   :refresh_token
                   :service_endpoint
                   :did
                   :dpop_private_key]
       :from      :identity.user
       :left-join [:atproto.access_credential
                   [:= :atproto.access_credential.did :identity.user.atproto_did]]
       :where     [:= :identity.user.id (if (string? user-id)
                                          (parse-uuid user-id)
                                          user-id)]})))

(defn refresh-credentials!
  [db user-id]
  'TODO)

(defn for-user
  [db user-id]
  (let [credentials (atom (refetch-credentials db user-id))]
    {::refetch-credentials (partial refetch-credentials db user-id)
     ::credentials-atom    credentials}))

(defn send-request
  [_client request-path & {:keys [credentials
                                  request]}]

  (let [dpop-key (ECKey/parse ^String (:access_credential/dpop_private_key credentials))
        proof-factory (DefaultDPoPProofFactory. dpop-key JWSAlgorithm/ES256)
        request-uri (str (:access_credential/service_endpoint credentials) request-path)
        access-token (AccessToken/parse (str "DPoP " (:access_credential/access_token credentials)) AccessTokenType/DPOP)
        dpop-jwt (fn [nonce]
                   (DPoPProofFactory/.createDPoPJWT
                     proof-factory
                     (:method request)
                     (URI. request-uri)                     ;; htu
                     access-token
                     (when nonce
                       (Nonce/parse nonce))))
        headers {"DPoP"          (SignedJWT/.serialize (dpop-jwt nil))
                 "Authorization" (str (AccessToken/.getType access-token) " " access-token)}]
    (try
      (http/request
        (-> request
            (update :headers merge headers)
            (assoc :url request-uri)))
      (catch ExceptionInfo e
        (let [error (some-> (:body (ex-data e))
                            (cheshire/parse-string-strict)
                            (get "error"))]
          (cond
            (= error "use_dpop_nonce")
            (http/request
              (-> request
                  (update :headers merge
                          (assoc headers
                            "DPoP" (SignedJWT/.serialize (dpop-jwt (get-in (ex-data e) [:headers "dpop-nonce"])))))
                  (assoc :url request-uri)))

            (= error "invalid_token")
            (throw e)

            :else
            (throw e)))))))

(defn com-atproto-repo-putRecord
  [{::keys [credentials-atom]
    :as    client}
   & {:keys [collection record rkey]}]
  (let [credentials @credentials-atom]
    (send-request client
                  "/xrpc/com.atproto.repo.putRecord"
                  {:credentials credentials
                   :request     {:body         (json/generate-string
                                                 {:repo       (:access_credential/did credentials)
                                                  :rkey       rkey
                                                  :collection collection
                                                  :record     (assoc record "$type" collection)})

                                 :content-type :json
                                 :method       "POST"}})))


(defn com-atproto-repo-uploadBlob
  [{::keys [credentials-atom]
    :as    client} & {:keys [body content-type]}]
  (let [credentials @credentials-atom]
    (send-request client
                  "/xrpc/com.atproto.repo.uploadBlob"
                  {:credentials credentials
                   :request     {:body         body
                                 :content-type content-type
                                 :method       "POST"}})))

(comment
  (def client (for-user
                (user/db)
                #_"019e70a9-076c-7971-ab11-d758d6e31620" ;;mccue.dev
                "019e70c9-7d6a-72cb-ae30-c44ffe64f6a8"))

  (def oracle-jdk
    ((requiring-resolve 'dev.mccue.repository.jmod/procure)
     ((requiring-resolve 'dev.mccue.repository.descriptors/oracle-jdk))))
  (println 3)


  (let [name-version-blob+infos (for [[name infos] (group-by (comp :name :module-info) oracle-jdk)]
                                  (let []
                                    [name
                                     (:version (:module-info (first infos)))
                                     (->> infos
                                          (map (fn [info]
                                                 (println "uploading a blob for " name ", " (:target-platform (:module-info info)))
                                                 (assoc info
                                                   :blob (-> (com-atproto-repo-uploadBlob client
                                                                                          :body (:bytes info)
                                                                                          :content-type "application/java-archive")
                                                             (:body)
                                                             (cheshire/parse-string-strict)
                                                             (get "blob"))))))]))]


    (doseq [[name version blob+infos] name-version-blob+infos]
      (println "About to put: " name)
      (Thread/sleep 5000)
      (com-atproto-repo-putRecord client
                                  :collection "dev.mccue.jvm.module"
                                  :rkey (str name
                                             (when-let [version version]
                                               (str ":" version)))
                                  :record {:indexMe   true
                                           :createdAt (str (OffsetDateTime/now))
                                           :variants (for [{:keys [blob target-platform]} blob+infos]
                                                       {:artifact blob
                                                        :operatingSystem (when-let [tp target-platform]
                                                                           (cond
                                                                             (= (string/starts-with? tp "windows"))
                                                                             "windows"

                                                                             (= (string/starts-with? tp "macos"))
                                                                             "macos"
                                                                             :else
                                                                             "linux"))
                                                        :cpuArchitecture (or (when-let [tp target-platform]
                                                                               (println (str name
                                                                                             (when-let [version version]
                                                                                               (str ":" version)))
                                                                                        "-" tp)
                                                                               (cond
                                                                                 (= (string/ends-with? tp "amd64"))
                                                                                 "amd64"

                                                                                 (= (string/ends-with? tp "aarch64"))
                                                                                 "aarch64"
                                                                                 :else
                                                                                 nil))
                                                                             (println "NO TARGET PLATFORM!"))})})))


  (doseq [artifact [(artifact/maven-central-artifact
                      :groupId "org.slf4j"
                      :artifactId "slf4j-api"
                      :version "2.0.18")
                    (artifact/maven-central-artifact
                      :groupId "org.slf4j"
                      :artifactId "slf4j-simple"
                      :version "2.0.18")
                    (artifact/maven-central-artifact
                      :groupId "dev.mccue"
                      :artifactId "jdbc"
                      :version "2025.10.07")
                    (artifact/maven-central-artifact
                      :groupId "dev.mccue"
                      :artifactId "json"
                      :version "2024.11.20")
                    (artifact/maven-central-artifact
                      :groupId "org.jspecify"
                      :artifactId "jspecify"
                      :version "1.0.0")]]

    (Thread/sleep 5000)
    (let [slf4j-api (with-open [is (io/input-stream (:url artifact))]
                      (.readAllBytes is))
          mi (dev.mccue.repository.artifact/module-info-from-archive-bytes slf4j-api)]
      (let [{:keys [body]}
            (com-atproto-repo-uploadBlob client
                                         :body slf4j-api
                                         :content-type "application/java-archive")]
        (println body)

        (com-atproto-repo-putRecord client
                                    :collection "dev.mccue.jvm.module"
                                    :rkey (str (:name mi)
                                               (when-let [version (:version mi)]
                                                 (str ":" version)))
                                    :record {:variants
                                             [{:artifact   (-> (cheshire/parse-string-strict body)
                                                               (get "blob"))

                                               :license    "Apache-2.0"}]
                                             :indexMe   true
                                             :createdAt (str (OffsetDateTime/now))})))))
(comment
  (defn from-credentials
    [credential])


  (def did "did:plc:2oip3ubsbe2pc7tmbnwsm3i7")
  (jdbc/execute-one!
    (user/db)
    ["SELECT * FROM identity.user WHERE atproto_did=?"
     did])

  (def cred (some-> (jdbc/execute-one!
                      (user/db)
                      ["SELECT * FROM atproto.access_credential WHERE did=?"
                       did])

                    (update-keys name)
                    (update-keys keyword))))


(defn send!
  [action method params])
