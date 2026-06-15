(ns dev.mccue.jvm.cli
  (:refer-clojure :exclude [resolve])
  (:gen-class)
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.pprint]
            [clojure.string :as string]
            [clojure.xml :as xml]
            [dev.mccue.atproto.cid :as cid]
            [dev.mccue.atproto.diddy :as diddy]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.io FileNotFoundException PrintStream)
           (java.lang ModuleLayer)
           (java.lang.module ModuleDescriptor$Version ResolvedModule)
           (java.nio.charset StandardCharsets)
           (java.nio.file FileAlreadyExistsException)
           (java.util StringJoiner)
           (java.util.spi ToolProvider)
           (org.sqlite SQLiteDataSource)
           (org.xml.sax SAXParseException)))

(def starter-xml
  "<?xml version='1.0' encoding='UTF-8'?>
<jvm>
    <provider>
        <handle>mccue.dev</handle>
        <did>...</did>
    </provider>

    <module>
        <provider>mccue.dev</provider>
        <name>dev.mccue.json</name>
        <version>2024.11.20</version>
    </module>
</jvm>")

(def init-spec
  {:force {:alias :f
           :coerce :boolean
           :default false}})

(defn init
  [{:keys [opts]}]
  (when (:force opts)
    (fs/delete-if-exists "jvm.xml"))
  (try
    (fs/write-bytes (fs/create-file "jvm.xml")
                    (String/.getBytes starter-xml StandardCharsets/UTF_8))
    (println "jvm.xml created")
    (catch FileAlreadyExistsException _
      (println "jvm.xml already exists"))))



(defn publish
  [_]
  (clojure.pprint/pprint _))

(def publish-spec
  {:username {:desc    "Username (handle) for your account on ATProto"
              :require true}
   :password {:desc    "Password for your account on ATProto"
              :require true}
   :force    {:desc    "Force publishing, even if another record already exists"
              :coerce  :boolean
              :default false}})

(def index-spec
  {:index-url  {:desc    "The host to use to retrieve the index."
                :default "https://jvm.mccue.dev/index.db"}
   :cache-path {:desc    "The directory to cache artifacts in."
                :default (str (fs/path ".jvm" "cache"))}})

(defn index
  [{:keys [opts]}]
  (let [path (:index-url opts)
        index-db-path (fs/path (:cache-path opts) "index.db")]
    (println "Downloading latest index from" path)
    (fs/create-dirs (:cache-path opts))
    (let [index-db (:body (http/get path
                                    {:as :byte-array}))]
      (fs/write-bytes (io/file (str index-db-path)) index-db))))

(def resolve-spec
  {:offline {:coerce :boolean
             :desc "Run module resolution offline"}
   :index-host {:desc    "The host to use to retrieve the index."
                :default "https://jvm.mccue.dev"}
   :cache-path {:desc    "The directory to cache artifacts in."
                :default (str (fs/path ".jvm" "cache"))}
   :artifact-host {:default "https://jvm.mccue.dev/module/"}})


(def ^:dynamic *crash!*
  (fn [& messages]
    (binding [*out* *err*]
      (println (string/join "" (map str messages)))
      (System/exit 1))))

(defn expect-string-content!
  [element error]
  (if-not (every? string? (:content element))
    (*crash!* error)
    (string/join "" (:content element))))

(defn interpret-provider
  [provider]
  (let [no-handle! #(*crash!* "<handle> not specified for provider")
        no-did!    #(*crash!* "<did> not specified for provider")]
    (let [{:keys [handle did]} (:attrs provider)]
      (cond
        (and (seq (:content provider))
             (or handle did))
        (*crash!* "Cannot provider module information both in xml attributes and as children")

        (or (some? handle) (some? did))
        (do
          (when (nil? handle) (no-handle!))
          (when (nil? did) (no-did!))
          {:handle (String/.strip handle) :did (String/.strip did)})

        :else
        (loop [[child & rest] (:content provider)
               handle         nil
               did            nil]
          (cond
            (nil? child)
            (cond
              (nil? handle)
              (no-handle!)

              (nil? did)
              (no-did!)

              :else
              {:handle (String/.strip handle) :did (String/.strip did)})

            (= (:tag child) :handle)
            (if handle
              (*crash!* "More than one <handle> specified for provider")
              (recur rest
                     (expect-string-content! child "Nested elements are not allowed as part of a <handle>")
                     did))

            (= (:tag child) :did)
            (if did
              (*crash!* "More than one <did> specified for provider")
              (recur rest
                     handle
                     (expect-string-content! child "Nested elements are not allowed as part of a <did>")))

            :else
            (*crash!* "Unknown element in <provider>: <" (name (:tag child)) ">")))))))

(defn interpret-module
  [module]
  (let [no-name! #(*crash!* "<name> not specified for module")
        no-provider! #(*crash!* "<provider> not specified for module")]
    (let [{:keys [name provider version]} (:attrs module)]
      (cond
        (and (seq (:content module))
             (or name provider version))
        (*crash!* "Cannot specify module information both in xml attributes and as children")

        (or (some? name) (some? provider) (some? version))
        (do
          (when (nil? name) (no-name!))
          (when (nil? provider) (no-provider!))
          {:name (String/.strip name)
           :provider (String/.strip provider)
           :version (some-> version (String/.strip))})

        :else
        (loop [[child & rest] (:content module)
               name           nil
               provider       nil
               version        nil]
          (cond
            (nil? child)
            (cond
              (nil? module)
              (*crash!* "<name> not specified for module")

              (nil? provider)
              (*crash!* "<provider> not specified for module")

              :else
              {:name (String/.strip name)
               :provider (String/.strip provider)
               :version (some-> version (String/.strip))})

            (= (:tag child) :name)
            (if name
              (*crash!* "More than one <name> specified for provider")
              (recur rest
                     (expect-string-content! child "Nested elements are not allowed as part of a <name>")
                     provider
                     version))


            (= (:tag child) :provider)
            (if provider
              (*crash!* "More than one <provider> specified for provider")
              (recur rest
                     name
                     (expect-string-content! child "Nested elements are not allowed as part of a <provider>")
                     version))

            (= (:tag child) :version)
            (if version
              (*crash!* "More than one <version> specified for provider")
              (recur rest
                     name
                     provider
                     (expect-string-content! child "Nested elements are not allowed as part of a <version>")))

            :else
            (*crash!* "Unknown element in <provider>: <" (name (:tag child)) ">")))))))

(defn interpret-xml
  [xml]
  (let [root-tag (:tag xml)]
    (if (not= root-tag :jvm)
      (*crash!* "Root element should be <jvm>, not <" (name root-tag) ">")
      (loop [[child & rest] (:content xml)
             providers      []
             modules        []]
        (cond
          (not child)
          {:providers providers
           :modules   modules}

          (= (:tag child) :provider)
          (recur
            rest
            (conj providers (interpret-provider child))
            modules)

          (= (:tag child) :module)
          (recur
            rest
            providers
            (conj modules (interpret-module child))))))))

(defn check-modules-have-listed-provider!
  [providers modules]
  (let [dids    (set (map :did providers))
        handles (set (map :handle providers))]
    (loop [[module & rest] modules]
      (when module
        (if (and (not (dids (:provider module)))
                 (not (handles (:provider module))))
          (*crash!* "Provider " (:provider module) " for module " (:name module) " not in list of providers")
          (recur rest))))))

(defn check-provider-handles-match-dids!
  [providers]
  (doseq [{:keys [handle did]} providers]
    (let [actual-handle (some-> (diddy/resolve-did-document did)
                                (diddy/resolve-handle))

          actual-did    (diddy/get-did handle)]
      (cond
        (nil? actual-handle)
        (*crash!* "Unable to resolve " did " to a handle.")

        (not= handle actual-handle)
        (*crash!* did " resolves to handle " actual-handle ", not " handle)

        (not= did actual-did)
        (*crash!* "Handle " handle " resolves to " actual-did ", not " did)))))

(defn get-system-modules
  []
  (-> (ModuleLayer/boot)
      (ModuleLayer/.configuration)
      (.modules)
      (->> (map ResolvedModule/.name))
      (set)))

(defn warn-for-system-modules!
  [system-modules modules]
  (doseq [{:keys [name]} modules]
    (when (system-modules name)
      (binding [*out* *err*]
        (println "warning: declared module" name "already provided by JVM")))))

(defn attach-dids-to-modules
  [providers modules]
  (let [handle->did (into {} (mapv #((juxt :handle :did) %) providers))]
    (mapv (fn [module]
            (let [provider (:provider module)]
              (if (string/starts-with? provider "did:")
                (assoc module :did provider)
                (assoc module :did (handle->did provider)))))
          modules)))

(defn check-for-missing-modules!
  [db system-modules modules]
  (let [missing-modules (loop [missing []
                               [module & rest] modules]
                          (if module
                            (let [{:keys [cid]} module
                                  requires (jdbc/execute! db (sql/format
                                                               {:select [:module_requires.module
                                                                         :module_requires.static
                                                                         :module_requires.version]
                                                                :from :module
                                                                :left-join [:module_requires [:= :module.id :module_requires.module_id]]
                                                                :where [:and
                                                                        [:= :module.cid cid]
                                                                        [:not :module_requires.static]]}))]
                              (recur (vec (concat missing
                                                  (->> requires
                                                       (filter (fn [req]
                                                                 (and (not (system-modules (:module_requires/module req)))
                                                                      (not ((set (map :name modules)) (:module_requires/module req))))))
                                                       (map (fn [req]
                                                              {:module (:name module)
                                                               :version (:version module)
                                                               :required-version (:module_requires/version req)
                                                               :missing (:module_requires/module req)})))))
                                     rest))
                            missing))]
    (when (seq missing-modules)
      (let [sj (StringJoiner. "\n")]
        (doseq [{:keys [module version missing required-version]} missing-modules]
          (StringJoiner/.add sj (str "Missing module "
                                     missing
                                     (when required-version
                                       (str "@" required-version))
                                     ", required by module "
                                     module
                                     (when version
                                       (str "@" version))))
          #_(let [providers-for-missing-module (->> (jdbc/execute! db (doto (sql/format
                                                                              {:select [:published_module.atproto_did :module.version]
                                                                               :from :published_module
                                                                               :left-join [:module [:= :published_module.module_id :module.id]]
                                                                               :where [:= :module.name missing]})
                                                                        (println)))
                                                    (map (fn [{:module/keys [version]
                                                               :as m}]
                                                           (if version
                                                             (assoc m :module/version (ModuleDescriptor$Version/parse version))
                                                             m)))
                                                    (group-by :published_module/atproto_did)
                                                    (update-vals (fn [versions] (map :module/version versions))))]
              (println providers-for-missing-module)))


        (*crash!* (StringJoiner/.toString sj))))))

(defn get-index
  [cache-path]
  (let [index-db-path (fs/path cache-path "index.db")]
    (when-not (fs/exists? index-db-path)
      (*crash!* "Module index not found"))
    (doto (SQLiteDataSource.)
      (.setUrl (str "jdbc:sqlite:" index-db-path)))))

(defn pick-module-variants
  [db modules]
  (loop [picked []
         [module & rest] modules]
    (if-not module
      picked
      ;; TODO: attributes should be considered too!
      (let [variants (jdbc/execute! db
                                    (sql/format
                                      {:select [:module.id
                                                :module.cid
                                                :module.name
                                                :module.version
                                                :module.target_platform]
                                       :from :published_module
                                       :left-join [:module [:= :module.id :published_module.module_id]]
                                       :where [:and [:= :published_module.atproto_did (:did module)]
                                               [:= :module.name (:name module)]
                                               [:= :module.version (:version module)]]}))
            os-name (System/getProperty "os.name")
            os-arch (System/getProperty "os.arch")
            expected-platform (str (cond
                                     (string/includes? (String/.toLowerCase os-name) "mac")
                                     "macos"

                                     (string/includes? (String/.toLowerCase os-name) "win")
                                     "windows"

                                     :else
                                     (*crash!* "Unhandled os " os-name))
                                   "-"
                                   (cond
                                     (= os-arch "aarch64")
                                     "aarch64"

                                     :else
                                     (*crash!* "Unhandled arch " os-arch)))
            variants (->> variants
                          (filter (fn [variant]
                                    (or (= expected-platform (:module/target_platform variant))
                                        (nil? (:module/target_platform variant))))))]
        (cond
          (= (count variants) 0)
          (*crash!* "No appropriate variant found for " (:name module) ". version=" (or (:version module) "<none>") ", target_platform=" expected-platform)

          (> (count variants) 1)
          (*crash!* "More than one appropriate variant found for " (:name module))

          :else
          (recur (conj picked
                       (assoc module :cid (:module/cid (first variants))))
                 rest))))))


(defn check-no-duplicate-modules!
  [modules]
  (loop [seen #{}
         [module & rest] modules]
    (when module
      (if (seen (:name module))
        (*crash!* "Duplicate declaration for " (:name module))
        (recur (conj seen (:name module)) rest)))))

(defn procure-modules!
  [{:keys [cache-path artifact-host]} modules]
  (fs/create-dirs cache-path)
  (fs/create-dirs (fs/path cache-path "blobs"))
  (fs/delete-tree (fs/path cache-path "modules"))
  (fs/create-dirs (fs/path cache-path "modules"))
  (doseq [module modules]
    (let [blob-path (fs/path cache-path "blobs" (:cid module))]
      (when-not (fs/exists? blob-path)
        (let [bytes (-> (http/get (str artifact-host (:cid module))
                                  {:as :byte-array})
                        (:body))
              bytes-cid (cid/bytes->cid-string bytes)]
          (when-not (= bytes-cid (:cid module))
            (*crash!* "Content ID of downloaded artifact does not match. expected="
                    (:cid module)
                    ", actual="
                    bytes-cid))
          (fs/write-bytes blob-path bytes)))
      (let [bytes   (fs/read-all-bytes blob-path)
            is-jmod (and (= (first bytes) (byte \J))
                         (= (second bytes) (byte \M)))
            module-path (fs/path cache-path
                                 "modules"
                                 (str (:name module)
                                      (when (:version module)
                                        (str "@" (:version module)))
                                      (if is-jmod
                                        ".jmod"
                                        ".jar")))]
        (fs/copy blob-path module-path)))))

(defn parse-xml 
  [path]
  (try
    (xml/parse (io/input-stream (io/file path)))
    (catch SAXParseException _
      (*crash!* "jvm.xml is malformed"))
    (catch FileNotFoundException _
      (*crash!* "jvm.xml not found"))))
(defn resolve
  [{:keys [opts]}]
  (let [xml (parse-xml "jvm.xml")
        {:keys [providers modules]} (interpret-xml xml)]
    (check-modules-have-listed-provider! providers modules)
    (when-not (:offline opts)
      (check-provider-handles-match-dids! providers))
    (check-no-duplicate-modules! modules)
    (let [system-modules (get-system-modules)]
      (warn-for-system-modules! system-modules modules)
      (let [db      (get-index (:cache-path opts))
            modules (attach-dids-to-modules providers modules)
            modules (pick-module-variants db modules)]
        (check-for-missing-modules! db system-modules modules)
        #_(warn-about-mismatched-versions! db system-modules modules)
        (procure-modules! opts modules)
        {:modules modules
         :system-modules system-modules}))))

(def link-spec
  (merge resolve-spec
         {:output            {:default "jvm"
                              :desc    "The output path for the linked JDK"}
          :minimal           {:coerce :boolean
                              :desc   "Whether to make a minimal JDK from the declared dependencies."
                              :default false}
          :include-incubator {:coerce :boolean
                              :desc "Whether to include incubator modules in the linked JDK"
                              :default false}}))


(defn link
  [{:keys [opts]}]
  (fs/delete-tree (:output opts))
  (let [{:keys [modules system-modules]} (resolve {:opts opts})]
    (let [jlink-tool (-> (ToolProvider/findFirst "jlink")
                         (.orElse nil))]
      (when-not jlink-tool
        (*crash!* "No jlink tool found in current JDK"))
      (let [jlink-args   (-> []
                             (conj "--output")
                             (conj (str (:output opts)))
                             (conj "--module-path")
                             (conj (str (fs/path (:cache-path opts) "modules")))
                             (conj "--add-modules")
                             (conj (string/join ","
                                                (if (:minimal opts)
                                                  (map :name modules)
                                                  (concat system-modules
                                                          (map :name modules))))))
            status-code (^[PrintStream PrintStream String/1]
                          ToolProvider/.run
                          jlink-tool
                          System/out
                          System/err
                          (into-array String jlink-args))]
        (when (not= status-code 0)
          (System/exit status-code))))))

(def table
  [{:cmds ["init"]
    :fn   init
    :spec init-spec
    :doc  "Creates a blank jvm.xml in the current directory"}
   {:cmds ["index"]
    :fn   index
    :spec index-spec
    :doc  "Download the latest index of modules."}
   {:cmds ["resolve"]
    :fn   resolve
    :spec resolve-spec
    :doc  "Resolve and procure dependencies without linking a JDK."}
   {:cmds ["link"]
    :fn   link
    :spec link-spec
    :doc "Link a JDK from the declared dependencies."}
   {:cmds ["publish-module"]
    :fn publish
    :spec publish-spec
    :doc  "Publish a module for use by other people"}])

(defn -main [& args]
  (cli/dispatch
    table
    args
    {:prog "jvm" :help true}))

;;  clojure -J--enable-native-access=ALL-UNNAMED -A:cli -M -m dev.mccue.jvm.cli modules
