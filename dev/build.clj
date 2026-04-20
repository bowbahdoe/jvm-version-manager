(ns build
  (:require [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def uber-file "target/jvm.jar")

;; delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn uber [_]
  (println @basis)
  (clean nil)
  (b/copy-dir {:src-dirs ["src"]
               :target-dir class-dir})
  (b/compile-clj {:basis @basis
                  :ns-compile '[dev.mccue.jdk]
                  :class-dir class-dir})
  (b/uber {:class-dir class-dir
           :uber-file uber-file
           :basis @basis
           :main 'dev.mccue.jdk
           :manifest {"Enable-Native-Access" "ALL-UNNAMED"}}))

(defn -main
  [& _]
  (uber {}))

(comment
  (build/-main))