(ns dev.mccue.repository.jmod
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.string :as string]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.repository.module-info :as mi]
            [next.jdbc :as jdbc])
  (:import (java.io ByteArrayInputStream File InputStream OutputStream PrintStream)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.security MessageDigest)
           (java.util HexFormat Optional)
           (java.util.spi ToolProvider)
           (java.util.zip GZIPInputStream)
           (javax.xml.parsers DocumentBuilder DocumentBuilderFactory)
           (javax.xml.xpath XPath XPathFactory)
           (org.apache.commons.compress.archivers ArchiveEntry ArchiveInputStream)
           (org.apache.commons.compress.archivers.tar TarArchiveInputStream)
           (org.apache.commons.compress.archivers.zip ZipArchiveInputStream)))


(defn determine-archive-type
  [artifact]
  (let [url (:url artifact)]
    (cond
      (str/ends-with? url ".zip")
      :zip

      (str/ends-with? url ".jar")
      :jar

      (str/ends-with? url ".tar.gz")
      :tar.gz

      :else
      (throw (ex-info (str "Unknown archive type: " url)
                      {:url url})))))




(defn archive->sequenced-kv
  [^ArchiveInputStream archive-input-stream]
  (loop [entries []]
    (let [entry (ArchiveInputStream/.getNextEntry archive-input-stream)]
      (if (nil? entry)
        entries
        (recur (conj entries
                     {:name (ArchiveEntry/.getName entry)
                      :bytes (InputStream/.readAllBytes archive-input-stream)}))))))


(defn- prepare-jmod-info!
  [fetch descriptor]
  (let [{:keys [name artifacts]} descriptor]
    {:name name
     :artifacts
     (vec (for [artifact artifacts]
            (let [fetched-artifact (fetch artifact)
                  artifact-bytes   (:bytes fetched-artifact)]
              (let [archive-entries (with-open [zis (case (determine-archive-type artifact)
                                                      :zip (ZipArchiveInputStream. (ByteArrayInputStream. artifact-bytes))
                                                      :tar.gz (TarArchiveInputStream. (GZIPInputStream.  (ByteArrayInputStream. artifact-bytes))))]
                                      (archive->sequenced-kv zis))]

                (loop [archive-entries archive-entries
                       jmod-info {:cmds            []
                                  :legal-notices   []
                                  :class-path      [{:name  "module-info.class"
                                                     :bytes (mi/to-bytes {:name    name
                                                                          :version (:version artifact)})}]
                                  :target-platform (:target-platform artifact)}]
                  (let [[entry & rest-entries] archive-entries]
                    (if (nil? entry)
                      jmod-info
                      (cond
                        (some (partial = (:name entry))
                              (:cmds artifact))
                        (recur rest-entries (update jmod-info :cmds conj entry))


                        (some (partial = (:name entry))
                              (:legal-notices artifact))
                        (recur rest-entries (update jmod-info :legal-notices conj entry))
                        :else
                        (recur rest-entries jmod-info)))))))))}))

;; https://gist.github.com/olieidel/c551a911a4798312e4ef42a584677397
(defn delete-directory-recursive
  "Recursively delete a directory."
  [file]
  (when (File/.isDirectory file)
    (run! delete-directory-recursive (File/.listFiles file)))
  (io/delete-file file))

(defn procure-jmod
  [options descriptor]
  (let [{:keys [name artifacts]} (prepare-jmod-info! (:fetch options) descriptor)]
    (mapv (fn [artifact]
            (let [temp-dir (Files/createTempDirectory "jmod" (into-array FileAttribute []))]
              (try
                (let [class-path (Path/.resolve temp-dir "class-path")
                      cmds (Path/.resolve temp-dir "cmds")
                      legal-notices (Path/.resolve temp-dir "legal-notices")
                      dump! (fn [root name bytes]
                              (let [outfile (^[String] Path/.resolve root name)]
                                (Files/createDirectories (Path/.getParent outfile)
                                                         (into-array FileAttribute []))
                                (with-open [os (io/output-stream (Path/.toFile outfile))]
                                  (io/copy (io/input-stream bytes) os))))]

                  (doseq [{:keys [name bytes]} (:class-path artifact)]
                    (dump! class-path name bytes))

                  (doseq [{:keys [name bytes]} (:cmds artifact)]
                    (dump! cmds name bytes))

                  (doseq [{:keys [name bytes]} (:legal-notices artifact)]
                    (dump! legal-notices name bytes))

                  (let [jmod-tool (Optional/.orElseThrow (ToolProvider/findFirst "jmod"))
                        filename (str (:name artifact) "-" (:target-platform artifact) ".jmod")
                        path (-> temp-dir (Path/.resolve filename))]
                    (^[PrintStream PrintStream String/1]
                      ToolProvider/.run
                      jmod-tool
                      System/out
                      System/err
                      (into-array
                        String
                        (cond-> ["create"
                                 "--class-path" (str class-path)]
                                (seq (:cmds artifact))
                                (concat ["--cmds" (str cmds)])

                                (seq (:legal-notices artifact))
                                (concat ["--legal-notices" (str legal-notices)])


                                (:target-platform artifact)
                                (concat ["--target-platform" (:target-platform artifact)])

                                :then
                                (concat [(str path)]))))
                    (with-open [is (io/input-stream (Path/.toFile path))]
                      (let [bytes (InputStream/.readAllBytes is)]
                        {:name            name
                         :type            :jmod
                         :target-platform (:target-platform artifact)
                         :bytes           bytes
                         :module-info     (artifact/module-info-from-archive-bytes bytes)}))))
                (finally (delete-directory-recursive (Path/.toFile temp-dir))))))
          artifacts)))

(defn select-mvn-keys
  [m]
  (reduce-kv (fn [state k v]
               (if (= (namespace k) "mvn")
                 (assoc state k v)
                 state))
             {}
             m))
(defn procure-jdk
  [options descriptor]
  (vec
    (mapcat
      (fn [artifact]
        (let [fetched-artifact ((:fetch options) artifact)
              artifact-bytes   (:bytes fetched-artifact)
              archive-entries  (with-open [zis (case (determine-archive-type artifact)
                                                 :zip (ZipArchiveInputStream. (ByteArrayInputStream. artifact-bytes))
                                                 :tar.gz (TarArchiveInputStream. (GZIPInputStream.  (ByteArrayInputStream. artifact-bytes))))]
                                 (archive->sequenced-kv zis))]
          (for [archive-entry archive-entries
                :when (string/ends-with? (:name archive-entry)
                                         ".jmod")]
            (let [module-info (artifact/module-info-from-archive-bytes (:bytes archive-entry))]
              {:name (as-> (:name archive-entry) _
                           (string/split _ #"/")
                           (last _)
                           (String/.substring _ 0 (String/.lastIndexOf _ ".jmod")))
               :bytes (:bytes archive-entry)
               :module-info     module-info
               :target-platform (:target-platform module-info)
               :type :jmod}))))

            
      (:artifacts descriptor))))

(defn add-module-info-to-jar
  [jar-bytes module-info]
  (let [temp-dir (Files/createTempDirectory "jar" (into-array FileAttribute []))]
    (try
      (let [in-path (Path/.resolve temp-dir "original.jar")
            out-path (Path/.resolve temp-dir "enriched.jar")]
        (with-open [os (io/output-stream (Path/.toFile in-path))
                    is (io/input-stream jar-bytes)]
          (io/copy is os))
        (mi/enrich-jar {:in-path (str in-path)
                        :out-path (str out-path)
                        :module-info (-> module-info
                                         (update :exports
                                                 (fn [exports]
                                                   (if (vector? exports)
                                                     exports
                                                     (if (nil? exports)
                                                       nil
                                                       (exports (str in-path)))))))})
        (with-open [is (io/input-stream (Path/.toFile out-path))]
          (InputStream/.readAllBytes is)))
      (finally (delete-directory-recursive (Path/.toFile temp-dir))))))

(defn procure-jar
  [options descriptor]
  (mapv (fn [artifact]
          (let [fetched-artifact (-> ((:fetch options) artifact)
                                     (dissoc :cached))
                temp-dir (Files/createTempDirectory "jar" (into-array FileAttribute []))
                module-bytes (if (:module-info descriptor)
                               (let [module-info (-> (:module-info descriptor)
                                                     (assoc :name (:name descriptor)))]
                                 (add-module-info-to-jar (:bytes fetched-artifact)
                                                         module-info))
                               (:bytes fetched-artifact))
                module-bytes (let [mi (artifact/module-info-from-archive-bytes module-bytes)
                                   new-mi (cond
                                            (and (not (:version mi))
                                                 (:mvn/version artifact))
                                            (assoc mi :version (:mvn/version artifact))

                                            (not (:version mi))
                                            (throw (ex-info (str "No version in module-info.")
                                                            {:artifact artifact
                                                             :module-info mi}))

                                            (and (:mvn/version artifact)
                                                 (not= (:version mi) (:mvn/version artifact)))
                                            (binding [*out* *err*]
                                              (println (str "Module version does not match maven version. module version="
                                                            (:version mi)
                                                            ", maven version="
                                                            (:mvn/version artifact)))
                                              mi)
                                            #_(throw (ex-info (str "Module version does not match maven version. module version="
                                                                   (:version mi)
                                                                   ", maven version="
                                                                   (:mvn/version artifact))
                                                              {:artifact artifact
                                                               :module-info mi}))

                                            :else
                                            mi)]
                               (if (not= mi new-mi)
                                 (add-module-info-to-jar module-bytes new-mi)
                                 module-bytes))]

            (try
              (merge (select-mvn-keys artifact)
                     {:name     (:name descriptor)
                      :type     :jar
                      :bytes    module-bytes
                      :module-info (artifact/module-info-from-archive-bytes module-bytes)})

              (finally (delete-directory-recursive (Path/.toFile temp-dir))))))
        (:artifacts descriptor)))




(defn validate
  [procured-artifact]
  (let [module-name (:name procured-artifact)
        module-info (with-open [zais (ZipArchiveInputStream. (ByteArrayInputStream. (:bytes procured-artifact)))]
                      (loop
                        []
                        (let [entry (ArchiveInputStream/.getNextEntry zais)]
                          (if-not (nil? entry)
                            (if (or (= (ArchiveEntry/.getName entry) "module-info.class")
                                    (= (ArchiveEntry/.getName entry) "classes/module-info.class")
                                    (re-matches #"META-INF/versions/([0-9]+)/module-info.class"
                                                (ArchiveEntry/.getName entry)))
                              (mi/from-bytes (ArchiveInputStream/.readAllBytes zais))
                              (recur))
                            (throw (ex-info "No module-info.class found" {:artifact procured-artifact}))))))]

    (when-not (= module-name (:name module-info))
      (throw (ex-info (str "Expected module name does not match name in module-info.class. expected="
                           module-name
                           ", actual="
                           (:name module-info))
                      {:expected-module-name module-name
                       :actual-module-info module-info})))

    (when-not (= (:target-platform procured-artifact) (:target-platform module-info))
      (throw (ex-info (str "Expected target platform does not match name in module-info.class. module="
                           (:name procured-artifact)
                           ", expected="
                           (:target-platform procured-artifact)
                           ", actual="
                           (:target-platform module-info))
                      {:expected-target-platform (:target-platform procured-artifact)
                       :actual-module-info module-info})))

    procured-artifact))

(defn view-metadata
  [& {:keys [repository groupId artifactId]}]
  (let [url (str repository
                 (string/replace groupId "." "/")
                 "/"
                 artifactId
                 "/"
                 "maven-metadata.xml")]
    (slurp url)))

(defn check-for-updates
  [descriptors]
  (doseq [descriptor descriptors]
    (doseq [artifact (:artifacts descriptor)]
      (when (:mvn/groupId artifact)
        (let [metadata (view-metadata
                         {:groupId (:mvn/groupId artifact)
                          :artifactId (:mvn/artifactId artifact)
                          :repository (:mvn/repository artifact)})]
          (let [dbf (DocumentBuilderFactory/newDefaultInstance)
                db  (DocumentBuilderFactory/.newDocumentBuilder dbf)
                doc (DocumentBuilder/.parse db (ByteArrayInputStream. (String/.getBytes metadata)))
                xpath (-> (XPathFactory/newDefaultInstance)
                          (XPathFactory/.newXPath))
                latest-release (string/trim
                                 (str
                                   (XPath/.evaluate
                                     xpath
                                     "//release"
                                     doc)))]
            (when (not= latest-release (:mvn/version artifact))
              (binding [*out* *err*]
                (println (str "New version available for "
                              (:mvn/groupId artifact)
                              "/"
                              (:mvn/artifactId artifact)
                              ". current="
                              (:mvn/version artifact)
                              ", new="
                              latest-release))))))))))

(defn procure
  ([descriptor]
   (procure {} descriptor))
  ([options descriptor]
   (let [options (if (:fetch options)
                   options
                   (assoc options :fetch artifact/fetch-artifact))]
     (check-for-updates [descriptor])
     (mapv validate
           (case (:type descriptor)
             :jmod (procure-jmod options descriptor)
             :jar  (procure-jar options descriptor)
             :jdk  (procure-jdk options descriptor)
             (throw (RuntimeException. (str "Descriptor must have :type of :jmod or :jar, not " (or (:type descriptor) "nil")))))))))

(defn maven-central-artifact
  [& {:keys [type groupId artifactId version classifier]}]
  (let [central "https://repo1.maven.org/maven2/"
        url     (str central
                     (string/replace groupId "." "/")
                     "/"
                     artifactId
                     "/"
                     version
                     "/"
                     (str artifactId (when classifier
                                       (str "-" classifier))
                          "-" version
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
       :mvn/repository central
       :mvn/groupId groupId
       :mvn/artifactId artifactId
       :mvn/version version
       :mvn/classifier classifier})))

(defn write-module
  [out-dir module]
  (let [{:keys [name type bytes]} module]
    (with-open [is (io/output-stream
                     (Path/.toFile (Path/of (str out-dir)
                                            (into-array String [(str name
                                                                     (case type
                                                                       :jar ".jar"
                                                                       :jmod ".jmod"
                                                                       (throw (RuntimeException. (str "type: " type)))))]))))]
      (^[byte/1] OutputStream/.write is bytes))))

(defn create-jdk-module-sets
  [db]
  (jdbc/with-transaction [t db]
    (let [java-bases (jdbc/execute! t ["SELECT group_concat(id) as ids, version, provider_id
                                        FROM repository.module
                                        WHERE name='java.base'
                                          AND id NOT IN (
                                            SELECT module_id
                                            FROM repository.module_set_element
                                          )
                                        GROUP BY version, provider_id"])
          java-bases (map (fn [{:module/keys [version provider_id]
                                :keys [ids]}]
                            {:version version
                             :provider-id provider_id
                             :ids     (->> (string/split ids #",")
                                           (map parse-long))})
                          java-bases)]
      (doseq [java-base java-bases]
        (let [modules (jdbc/execute!
                        t
                        (vec (concat [(str "SELECT id, name, target_platform
                                            FROM repository.module
                                            WHERE name IN (
                                              SELECT module FROM module_hash
                                              WHERE module_id IN ("
                                           (string/join "," (repeat (count (:ids java-base)) "?"))
                                           "))")]
                                     (:ids java-base))))]
          (when-let [module-set (jdbc/execute-one! t ["INSERT INTO repository.module_set(name, version, description)
                                                       VALUES (?, ?, ?)
                                                       ON CONFLICT DO NOTHING
                                                       RETURNING id"
                                                      "JDK"
                                                      (:version java-base)
                                                      (str "JDK " (:version java-base))])]
            (doseq [java-base-id (:ids java-base)]
              (jdbc/execute! t ["INSERT INTO repository.module_set_element(module_id, module_set_id)
                                 VALUES (?, ?)"
                                java-base-id
                                (:module_set/id module-set)]))
            (doseq [module modules]
              (jdbc/execute! t ["INSERT INTO repository.module_set_element(module_id, module_set_id)
                                 VALUES (?, ?)"
                                (:module/id module)
                                (:module_set/id module-set)]))))))))

(comment
  (do (require '[dev.mccue.repository.descriptors])
      (require '[dev.mccue.repository.repository :as rep])
    (let [db (user/db)]
      (doseq [artifact (procure {:fetch (partial #'artifact/fetch-artifact-cached db)}
                                (dev.mccue.repository.descriptors/oracle-jdk))]
        (rep/persist-module db artifact))))

  (do (require '[dev.mccue.repository.descriptors])
    (require '[dev.mccue.repository.repository :as rep])
      (let [db (user/db)]
        (doseq [artifact (procure {:fetch (partial #'artifact/fetch-artifact-cached db)}
                                  (dev.mccue.repository.descriptors/just))]
          (rep/persist-module db artifact))))

  (do (require '[dev.mccue.repository.descriptors])
      (require '[dev.mccue.repository.repository :as rep])
      (let [descriptors (->> (ns-publics 'dev.mccue.repository.descriptors)
                             (vals)
                             (filterv (comp :descriptor meta))
                             (mapv (fn [f] (f))))]
        ;;version available for org.eclipse.jetty/jetty-server. current=12.1.5, new=12.1.8
        (doseq [descriptor descriptors]
          (println "-----")
          (println (:name descriptor))
          (try (doseq [artifact (procure {:fetch (partial artifact/fetch-artifact-cached (user/db))} descriptor)]
                 (rep/persist-module (user/db) artifact))
               (catch Exception e (Exception/.printStackTrace e))))))
  (do (require '[dev.mccue.repository.descriptors])
      (require '[dev.mccue.repository.repository :as rep])
      (let [count       (read-line)
            descriptors (take (parse-long count)
                              (dev.mccue.repository.descriptors/get-all-from-index))
            db          (user/db)]
        (doseq [descriptor (sort-by :name descriptors)]
          (println "-----")
          (println (:name descriptor))
          (try (doseq [artifact (procure {:fetch (partial artifact/fetch-artifact-cached db)} descriptor)]
                 (rep/persist-module db artifact))
               (catch Exception e (Exception/.printStackTrace e)))))))

(comment
  (defn dump [d]
    (let [d (procure d)]
      (Files/write (.toPath (File. (str (:name (:module-info d)) ".jar")))
                   (:bytes d)
                   (into-array java.nio.file.OpenOption [])))))