(ns dev.mccue.repository.artifact
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [dev.mccue.atproto.cid :as cid]
            [dev.mccue.repository.module-info :as mi]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.io ByteArrayInputStream InputStream)
           (java.security MessageDigest)
           (java.util HexFormat)
           (org.apache.commons.compress.archivers ArchiveEntry ArchiveInputStream)
           (org.apache.commons.compress.archivers.zip ZipArchiveInputStream)))

(defn maven-central-artifact
  [& {:keys [type groupId artifactId version classifier
             repository]
      :or {repository "https://repo1.maven.org/maven2/"
           type :jar}}]
  (let [url     (str repository
                     (string/replace groupId "." "/")
                     "/"
                     artifactId
                     "/"
                     version
                     "/"
                     (str artifactId
                          "-" version
                          (when classifier
                            (str "-" classifier))
                          (if type
                            (case type
                              :jar ".jar"
                              type)
                            ".jar")))
        sha256 (try (slurp (str url ".sha256"))
                    (catch Exception _ nil))
        sha512 (try (slurp (str url ".sha512"))
                    (catch Exception _ nil))
        sha1 (try (slurp (str url ".sha1"))
                  (catch Exception _ nil))
        md5 (try (slurp (str url ".md5"))
                 (catch Exception _ nil))]
    (let [hashes (cond-> {}
                         sha256 (assoc "sha256" sha256)
                         sha512 (assoc "sha512" sha512)
                         sha1 (assoc "sha1" sha1)
                         md5 (assoc "md5" md5))]
      (when-not (seq hashes)
        (binding [*out* *err*]
          (println "No hashes found in repo for" groupId artifactId)))
      {:url url
       :hashes hashes
       :type type
       :mvn/repository repository
       :mvn/groupId groupId
       :mvn/artifactId artifactId
       :mvn/version version
       :mvn/classifier classifier})))

(defn- check-hash!
  [artifact-description bytes]
  (doseq [[hash-type hash-value] (:hashes artifact-description)]
    (let [hash-type (name hash-type)
          hasher    (if (String/.equalsIgnoreCase "cid" hash-type)
                      cid/bytes->cid-string
                      #(let [digest (MessageDigest/getInstance (string/upper-case hash-type))]
                         (HexFormat/.formatHex (HexFormat/of)
                                               (MessageDigest/.digest digest %))))

          actual-hash    (hasher bytes)
          predicted-hash hash-value]
      (when (not= actual-hash predicted-hash)
        (throw (ex-info "Hash of artifact does not match" {:artifact-description artifact-description
                                                           :predicted-hash predicted-hash
                                                           :actual-hash actual-hash}))))))


(defn fetch-artifact
  [artifact]
  (let [url (:url artifact)
        artifact-bytes (with-open [artifact-stream (io/input-stream url)]
                         (InputStream/.readAllBytes artifact-stream))]
    (check-hash! artifact artifact-bytes)
    (merge artifact
           {:bytes artifact-bytes})))

(defn retrieve-artifact
  [db cid]
  (:artifact/data
    (jdbc/execute-one! db [(String/.stripIndent
                             "SELECT cid, data
                              FROM repository.artifact
                              WHERE cid = ?")
                           cid])))


(defn persist-artifact
  [db artifact]
  (let [cid (cid/bytes->cid-string (:bytes artifact))]
    (jdbc/execute! db (sql/format {:insert-into :repository.artifact
                                   :columns     [:cid :data]
                                   :values      [[cid (:bytes artifact)]]
                                   :on-conflict []
                                   :do-nothing  true}))
    cid))

(defn fetch-artifact-cached
  [db artifact]
  (let [{:keys [hashes]} artifact
        {:strs [sha256 cid]} hashes]
    (or (and sha256 (some->> (retrieve-artifact db (cid/sha256-hex-string->cid-string sha256))
                             (assoc {:cached true} :bytes)))
        (and cid (some->> (retrieve-artifact db cid)
                          (assoc {:cached true} :bytes)))
        (let [procured (fetch-artifact artifact)]
          (persist-artifact db procured)
          procured))))

(defn module-info-entries-from-archive
  [bytes]
  (with-open [zais (ZipArchiveInputStream. (ByteArrayInputStream. bytes))]
    (let [module-info-entries  (loop [entries []]
                                 (let [entry (ArchiveInputStream/.getNextEntry zais)]
                                   (if (nil? entry)
                                     entries
                                     (let [name (ArchiveEntry/.getName entry)]
                                       (if (or (= name "module-info.class")
                                               (= name "classes/module-info.class")
                                               (re-matches #"META-INF/versions/([0-9]+)/module-info.class"
                                                           name))
                                         (recur (conj entries
                                                      {:name name
                                                       :bytes (InputStream/.readAllBytes zais)}))
                                         (recur entries))))))]
      module-info-entries)))

(defn module-info-from-archive-bytes
  ([bytes & {:keys [no-module-info-found-cb
                    more-than-one-module-info-found-cb]}]
   (let [module-info-entries  (module-info-entries-from-archive bytes)]
     (cond
       (empty? module-info-entries)
       (if no-module-info-found-cb
         (no-module-info-found-cb)
         (throw (ex-info "No module-info.class in archive" {})))

       (> (count module-info-entries) 1)
       (if more-than-one-module-info-found-cb
         (more-than-one-module-info-found-cb module-info-entries)
         (throw (ex-info "More than one module-info.class in archive" {})))

       :else
       (mi/from-bytes (:bytes (first module-info-entries)))))))

