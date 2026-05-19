(ns dev.mccue.repository.descriptors
  (:require
    [clojure.string :as string]
    [dev.mccue.repository.module-info :as mi]
    [dev.mccue.repository.jmod :refer [maven-central-artifact]])
  (:import (java.io StringReader)
           (java.nio.charset StandardCharsets)
           (java.util Map$Entry Properties)))


(defn ^:descriptor oracle-jdk
  []
  {:type :jdk
   :artifacts [{:url "https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.zip"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/jdk-25_windows-x64_bin.zip.sha256")}}
               {:url "https://download.oracle.com/java/25/latest/jdk-25_macos-aarch64_bin.tar.gz"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/jdk-25_macos-aarch64_bin.tar.gz.sha256")}}
               {:url "https://download.oracle.com/java/25/latest/jdk-25_macos-x64_bin.tar.gz"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/jdk-25_macos-x64_bin.tar.gz.sha256")}}
               {:url "https://download.oracle.com/java/25/latest/jdk-25_linux-aarch64_bin.tar.gz"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/jdk-25_linux-aarch64_bin.tar.gz.sha256")}}
               {:url "https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/jdk-25_linux-x64_bin.tar.gz.sha256")}}]})

(defn ^:descriptor javafx
  []
  {:type :jdk
   :artifacts [{:url "https://download.oracle.com/java/25/latest/javafx-25_windows-x64_bin-jmods.zip"
                :hashes {"sha256" (slurp "https://download.oracle.com/java/25/latest/javafx-25_windows-x64_bin-jmods.zip.sha256")}}]})
(defn ^:descriptor just
  []
  {:name "just"
   :type :jmod
   :artifacts [{:url "https://github.com/casey/just/releases/download/1.48.1/just-1.48.1-x86_64-pc-windows-msvc.zip"
                :hashes {"sha256" "368cd9ca827cba04d9e6fc00f7ad840773c4605b6f64b9f87bdb00325d351029"}
                :cmds ["just.exe"]
                :legal-notices ["LICENSE", "README.md"]
                :target-platform "windows-amd64"
                :version "1.48.1"}
               {:url "https://github.com/casey/just/releases/download/1.48.1/just-1.48.1-aarch64-apple-darwin.tar.gz"
                :hashes {"sha256" "03a73339ff55bcf7411a3c940cdcb0a726d98134b87203c83a9008575434e2a8"}
                :cmds ["just.exe"]
                :legal-notices ["LICENSE", "README.md"]
                :target-platform "macos-aarch64"
                :version "1.48.1"}]})

(defn ^:descriptor vegeta
  []
  {:name "vegeta"
   :type :jmod
   :artifacts [{:url "https://github.com/tsenart/vegeta/releases/download/v12.13.0/vegeta_12.13.0_windows_amd64.zip"
                :hashes {"sha256" "421362edb7c8e1b97db9faf43806d2a8850067afc97c2f1bf9bc5cdcdfc9c0b6"}
                :cmds ["vegeta.exe"]
                :target-platform "windows-amd64"
                :version "12.13.0"}]})

(defn ^:descriptor clojure
  []
  (let [version "1.12.4"]
    {:name "org.clojure"
     :type :jar
     :module-info {:requires [{:module "java.xml"}
                              {:module "java.desktop"}
                              {:module "java.sql"}
                              {:module "jdk.unsupported"
                               :static true}
                              {:module "org.clojure.core.specs.alpha"}
                              {:module "org.clojure.spec.alpha"}]
                   :version version
                   :open true
                   :exports (fn [path]
                              (mapv (fn [package]
                                      {:package package})
                                    (mi/packages-in-jar path)))}
     :artifacts [(maven-central-artifact
                   :groupId "org.clojure"
                   :artifactId "clojure"
                   :version version)]}))

(defn ^:descriptor spec-alpha
  []
  (let [version "0.6.249"]
    {:name "org.clojure.spec.alpha"
     :type :jar
     :module-info {:name "org.clojure.spec.alpha"
                   :version version
                   :open true
                   :exports (fn [path]
                              (mapv (fn [package]
                                      {:package package})
                                    (mi/packages-in-jar path)))}
     :artifacts [(maven-central-artifact
                   :groupId "org.clojure"
                   :artifactId "spec.alpha"
                   :version version)]}))

(defn ^:descriptor core-specs-alpha
  []
  (let [version "0.5.81"]
    {:name "org.clojure.core.specs.alpha"
     :type :jar
     :module-info {:name "org.clojure.core.specs.alpha"
                   :version version
                   :open true}

     :artifacts [(maven-central-artifact
                   :groupId "org.clojure"
                   :artifactId "core.specs.alpha"
                   :version version)]}))

(defn ^:descriptor bb
  []
  (let [version "1.12.217"]
    {:name "bb"
     :type :jmod
     :artifacts [{:url (String/.formatted
                         "https://github.com/babashka/babashka/releases/download/v%s/babashka-%s-windows-amd64.zip"
                         (into-array Object [version version]))
                  :hashes {"sha256" "74fec166d2a7af69da022a78d205405765fa1278b3b4b93caf7db0401cbcbff1"}
                  :cmds ["bb.exe"]
                  :target-platform "windows-amd64"
                  :version version}]}))

(defn ^:descriptor org-jspecify
  []
  {:name "org.jspecify"
   :type :jar
   :artifacts [(maven-central-artifact
                 :groupId "org.jspecify"
                 :artifactId "jspecify"
                 :version "1.0.0")]})

(defn ^:descriptor com-github-slugify
  []
  (let [version "3.0.7"]
    {:name "slugify"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "com.github.slugify"
                   :artifactId "slugify"
                   :version version)]}))

(defn ^:descriptor dev-mccue-json
  []
  (let [version "2024.11.20"]
    {:name "dev.mccue.json"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "dev.mccue"
                   :artifactId "json"
                   :version version)]}))

(defn ^:descriptor dev-mccue-jdbc
  []
  (let [version "2025.10.07"]
    {:name "dev.mccue.jdbc"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "dev.mccue"
                   :artifactId "jdbc"
                   :version version)]}))

(defn ^:descriptor commons-compress
  []
  (let [version "1.28.0"]
    {:name "org.apache.commons.compress"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.apache.commons"
                   :artifactId "commons-compress"
                   :version version)]}))

(defn ^:descriptor commons-lang3
  []
  (let [version "3.20.0"]
    {:name "org.apache.commons.lang3"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.apache.commons"
                   :artifactId "commons-lang3"
                   :version version)]}))

(defn ^:descriptor commons-codec
  []
  (let [version "1.21.0"]
    {:name "org.apache.commons.codec"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "commons-codec"
                   :artifactId "commons-codec"
                   :version version)]}))


(defn ^:descriptor commons-io
  []
  (let [version "2.21.0"]
    {:name "org.apache.commons.io"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "commons-io"
                   :artifactId "commons-io"
                   :version version)]}))

(defn ^:descriptor dev-mccue-jdk-httpserver
  []
  (let [version "2024.11.18"]
    {:name "dev.mccue.jdk.httpserver"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "dev.mccue"
                   :artifactId "jdk-httpserver"
                   :version version)]}))

(defn ^:descriptor com-zaxxer-HikariCP
  []
  (let [version "7.0.2"]
    {:name "com.zaxxer.hikari"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "com.zaxxer"
                   :artifactId "HikariCP"
                   :version version)]}))

(defn ^:descriptor jetty-http-spi
  []
  (let [version "12.1.8"]
    {:name "org.eclipse.jetty.http.spi"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.eclipse.jetty"
                   :artifactId "jetty-http-spi"
                   :version version)]}))

(defn ^:descriptor jetty-server
  []
  (let [version "12.1.8"]
    {:name "org.eclipse.jetty.server"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.eclipse.jetty"
                   :artifactId "jetty-server"
                   :version version)]}))

(defn ^:descriptor jetty-http
  []
  (let [version "12.1.8"]
    {:name "org.eclipse.jetty.http"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.eclipse.jetty"
                   :artifactId "jetty-http"
                   :version version)]}))

(defn ^:descriptor jetty-util
  []
  (let [version "12.1.8"]
    {:name "org.eclipse.jetty.util"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.eclipse.jetty"
                   :artifactId "jetty-util"
                   :version version)]}))

(defn ^:descriptor jetty-io
  []
  (let [version "12.1.8"]
    {:name "org.eclipse.jetty.io"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.eclipse.jetty"
                   :artifactId "jetty-io"
                   :version version)]}))

(defn ^:descriptor jsoup
  []
  (let [version "1.22.1"]
    {:name "org.jsoup"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.jsoup"
                   :artifactId "jsoup"
                   :version version)]}))

(defn ^:descriptor jemoji
  []
  (let [version "1.7.6"]
    {:name "net.fellbaum.jemoji"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "net.fellbaum"
                   :artifactId "jemoji"
                   :version version)]}))

(defn ^:descriptor slf4j
  []
  (let [version "2.0.17"]
    {:name "org.slf4j"
     :type :jar
     :artifacts [(maven-central-artifact
                   :groupId "org.slf4j"
                   :artifactId "slf4j-api"
                   :version version)]}))

(defn extract-mvn-info
  [url]
  (let [[_ version artifact & group] (reverse (rest (rest (rest (rest (string/split url #"/"))))))]
    {:mvn/version version
     :mvn/artifactId artifact
     :mvn/groupId (string/join "." (reverse group))}))


(defn for-each-in-index
  [cb]
  (let [index (doto (Properties.)
                (Properties/.load
                  (StringReader. (slurp "https://raw.githubusercontent.com/sormuras/modules/refs/heads/main/com.github.sormuras.modules/com/github/sormuras/modules/modules.properties"))))]
    (doall
      (for [entry index]
        (cb {:name (Map$Entry/.getKey entry)
             :url (Map$Entry/.getValue entry)})))))

(defn get-all-from-index
  []
  (for-each-in-index
    (fn [{:keys [name url]}]
      {:name name
       :type :jar
       :artifacts [(merge {:url url}
                          (extract-mvn-info url))]})))




