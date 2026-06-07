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

;; TODO: rename exports To to
(defn refetch-credentials
  [db did]
  (jdbc/execute-one!
    db
    (sql/format
      {:select    [:scopes
                   :access_token
                   :refresh_token
                   :service_endpoint
                   :did
                   :dpop_private_key]
       :from      :atproto.access_credential
       :where     [:= :atproto.access_credential.did did]})))

(defn refresh-credentials!
  [db user-id]
  'TODO)

(defn for-did
  [db did]
  (let [credentials (atom (refetch-credentials db did))]
    {::refetch-credentials (partial refetch-credentials db did)
     ::credentials-atom    credentials}))

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
  (def client (for-did
                (user/db)
                #_"did:plc:dt7fth2hmap6wya7uyyl2g3v" ;;mccue.dev
                "did:plc:2oip3ubsbe2pc7tmbnwsm3i7"))

  (def oracle-jdk
    ((requiring-resolve 'dev.mccue.repository.jmod/procure)
     ((requiring-resolve 'dev.mccue.repository.descriptors/oracle-jdk))))
  (println 3)

  (def just
    ((requiring-resolve 'dev.mccue.repository.jmod/procure)
     ((requiring-resolve 'dev.mccue.repository.descriptors/just))))

  (def jq
    ((requiring-resolve 'dev.mccue.repository.jmod/procure)
     {:name "jq"
      :type :jmod
      :artifacts [{:url "file:///Users/emccue/Development/curler/jq.jmod"}]}))

  (def vegeta
    ((requiring-resolve 'dev.mccue.repository.jmod/procure)
     ((requiring-resolve 'dev.mccue.repository.descriptors/vegeta))))


  (defn consume-many
    [i])

  (let [name-version-blob+infos (for [[name infos] (group-by (comp :name :module-info) vegeta)]
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
      (Thread/sleep 1000)
      (com-atproto-repo-putRecord client
                                  :collection "dev.mccue.jvm.module"
                                  :rkey (str name
                                             (when-let [version version]
                                               (str ":" version)))
                                  :record {:createdAt (str (OffsetDateTime/now))
                                           :variants  (for [{:keys [blob] :as info} blob+infos]
                                                        {:artifact        blob})})))
  (ingest {:url "file:///Users/emccue/Development/curler/jq.jmod"})
  (defn ingest
    [artifact]
    (let [artifact-bytes (with-open [is (io/input-stream (:url artifact))]
                           (.readAllBytes is))
          mi (dev.mccue.repository.artifact/module-info-from-archive-bytes artifact-bytes)]
      (println "Uploading Blob ")
      (let [{:keys [body]}
            (com-atproto-repo-uploadBlob client
                                         :body artifact-bytes
                                         :content-type "application/java-archive")]

        (com-atproto-repo-putRecord client
                                    :collection "dev.mccue.jvm.module"
                                    :rkey (str (:name mi)
                                               (when-let [version (:version mi)]
                                                 (str ":" version)))
                                    :record {:variants
                                             [{:artifact   (-> (cheshire/parse-string-strict body)
                                                               (get "blob"))
                                               :attributes [{:name "source"
                                                             :value (:purl artifact)}]}]
                                             :createdAt (str (OffsetDateTime/now))}))))

    (doseq [artifact (:artifacts  (dev.mccue.repository.descriptors/vegeta))]
      (ingest (assoc artifact :purl (:url artifact))))

  (do (require '[dev.mccue.repository.descriptors])
      (require '[dev.mccue.repository.repository :as rep])
      (require '[dev.mccue.repository.jmod])
      (let [count       (read-line)
            descriptors (take (parse-long count)
                              (dev.mccue.repository.descriptors/get-all-from-index))
            db          (user/db)]
        (doseq [descriptor (sort-by :name descriptors)]
          (println "-----")
          (println (:name descriptor))
          (try (doseq [artifact (dev.mccue.repository.jmod/procure {:fetch (partial artifact/fetch-artifact-cached db)} descriptor)]
                 (let [name-version-blob+infos (for [[name infos] (group-by (comp :name :module-info) artifact)]
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
                     (Thread/sleep 1000)
                     (com-atproto-repo-putRecord client
                                                 :collection "dev.mccue.jvm.module"
                                                 :rkey (str name
                                                            (when-let [version version]
                                                              (str ":" version)))
                                                 :record {:createdAt (str (OffsetDateTime/now))
                                                          :variants  (for [{:keys [blob] :as info} blob+infos]
                                                                       {:artifact        blob})}))))
               (catch Exception e (Exception/.printStackTrace e))))))
    (ingest (assoc
              (artifact/maven-central-artifact
                :groupId "com.fasterxml.jackson.core"
                :artifactId "jackson-core"
                :version "2.22.0")
              :purl "pkg:maven/com.fasterxml.jackson.core/jackson-core@2.22.0"))

    (doseq [artifact [(assoc
                        (artifact/maven-central-artifact
                          :groupId "org.slf4j"
                          :artifactId "slf4j-api"
                          :version "2.0.18")
                        :purl "pkg:maven/org.slf4j/slf4j-api@2.0.18")
                      (assoc
                        (artifact/maven-central-artifact
                          :groupId "org.slf4j"
                          :artifactId "slf4j-simple"
                          :version "2.0.18")
                        :purl "pkg:maven/org.slf4j/slf4j-simple@2.0.18")
                      (assoc
                        (artifact/maven-central-artifact
                          :groupId "dev.mccue"
                          :artifactId "jdbc"
                          :version "2025.10.07")
                        :purl "pkg:maven/dev.mccue/jdbc@2025.10.07")
                      (assoc
                        (artifact/maven-central-artifact
                          :groupId "dev.mccue"
                          :artifactId "json"
                          :version "2024.11.20")
                        :purl "pkg:maven/dev.mccue/json@2024.11.20")
                      (assoc
                        (artifact/maven-central-artifact
                          :groupId "org.jspecify"
                          :artifactId "jspecify"
                          :version "1.0.0")
                        :purl "pkg:maven/org.jspecify/jspecify@1.0.0")
                      (assoc
                        (artifact/maven-central-artifact
                          :groupId "dev.mccue"
                          :artifactId "symbol"
                          :version "2025.06.06")
                        :purl "pkg:maven/dev.mccue/symbol@2025.06.06")]]

      (Thread/sleep 1000)
      (ingest artifact)))

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
