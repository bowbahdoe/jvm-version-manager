(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/jigsaw.jar")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"
                                   :aliases [:cli]})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (clean nil)
  (b/copy-dir {:src-dirs ["cli"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[dev.mccue.jvm.cli]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'dev.mccue.jvm.cli
           :manifest {"Enable-Native-Access" "ALL-UNNAMED"}}))

(defn -main
  [& _]
  (uber {}))

(comment
  (build/-main))