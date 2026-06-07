(ns dev.mccue.jvm.cli
  (:refer-clojure :exclude [resolve])
  (:gen-class)
  (:require [babashka.cli :as cli]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as string]
            [clojure.xml :as xml]
            [clojure.pprint]
            [clojure.zip :as zip])
  (:import (java.io FileNotFoundException InputStream)
           (java.nio.charset StandardCharsets)
           (java.nio.file FileAlreadyExistsException)
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

(def install-spec
  {:index-host {:desc    "The host to use to retrieve the index."
                :default "https://jvm.mccue.dev"}
   :cache-path {:desc    "The directory to cache artifacts in."
                :default (str (fs/path ".jvm" "cache"))}
   :minimal    {:coerce :boolean
                :desc   "Whether to make a minimal JDK from the declared dependencies."
                :default false}})

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


(defn install
  [{:keys [opts args]}])

(defn publish
  [_]
  (clojure.pprint/pprint _))

(def publish-spec
  {:username {:desc    "Username (handle) for your account on ATProto"}
   :password {:desc    "Password for your account on ATProto"}
   :force    {:desc    "Force publishing, even if another record already exists"
              :coerce  :boolean
              :default false}})


(defn stage
  [_]
  (clojure.pprint/pprint _))

(def stage-spec
  {:attribute {:alias :a
               :coerce []}
   :module  {:alias :m
             :require true}})

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
    (with-open [index-db (io/input-stream path)]
      (with-open [cache-index-db (io/output-stream (io/file (str index-db-path)))]
        (InputStream/.transferTo index-db cache-index-db)))))

(def resolve-spec
  {})

(defn crash!
  [& messages]
  (binding [*out* *err*]
    (println (string/join "" (map str messages)))
    (System/exit 1)))

(defn interpret-provider
  [provider]
  provider)

(defn interpret-module
  [module]
  module)

(defn interpret-xml
  [xml]
  (let [root-tag (:tag xml)]
    (if (not= root-tag :jvm)
      (crash! "Root element should be <jvm>, not <" (name root-tag) ">")
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


(defn resolve
  [_]
  (when-let [xml (try
                   (xml/parse (io/input-stream (io/file "jvm.xml")))
                   (catch SAXParseException _
                     (crash! "jvm.xml is malformed"))
                   (catch FileNotFoundException _
                     (crash! "jvm.xml not found")))]
    (clojure.pprint/pprint (interpret-xml xml))))


(def table
  [{:cmds ["init"]
    :fn   init
    :spec init-spec}
   {:cmds ["index"]
    :fn   index
    :spec index-spec
    :doc  "Download the latest index of modules."}
   {:cmds ["verify"]}
   {:cmds ["resolve"]
    :fn   resolve
    :spec resolve-spec
    :doc  "Resolve dependencies without linking a JDK."}
   {:cmds ["install"]
    :fn   install
    :spec install-spec}
   {:cmds ["stage"]
    :fn stage
    :spec stage-spec}
   {:cmds ["publish"]
    :fn publish
    :spec publish-spec}])

(defn -main [& args]
  (cli/dispatch
    table
    args
    {:prog "jvm" :help true}))

;;  clojure -J--enable-native-access=ALL-UNNAMED -A:cli -M -m dev.mccue.jvm.cli modules
