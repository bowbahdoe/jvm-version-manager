(ns dev.mccue.jdk
  (:import (java.nio.file Files LinkOption Path)
           (dev.mccue.color.terminal ANSIColor TerminalStyle TerminalStyle$Builder))
  (:require [clojure.set :as set]
            [dev.mccue.repository :as rep]
            [next.jdbc :as jdbc])
  (:gen-class))

(def jdk-edn-path "jdk.edn")
(defn init-jdk-edn!
  []
  (if-not (Files/exists (Path/of jdk-edn-path (into-array String []))
                        (into-array LinkOption []))
    (let [edn {}]
      (spit jdk-edn-path edn)
      edn)
    (read-string (slurp jdk-edn-path))))

(defn enrich
  [db edn]
  (reduce-kv
    (fn [new-edn module-name {:keys [provider
                                     version
                                     target-platform]
                              :as info}]
      (let [reqs (jdbc/execute! db ["SELECT module_id, module, static
                                      FROM module_requires
                                      WHERE module_id IN (
                                        SELECT id
                                        FROM module
                                        WHERE name = ?
                                          AND version = ?
                                          AND target_platform = ?
                                          AND (provider_id IS NULL OR provider_id IN (
                                            SELECT id
                                            FROM provider
                                            WHERE name=?
                                          )
                                        )
                                      )"
                                    (str module-name)
                                    version
                                    (or target-platform "universal")
                                    provider])]
        (assoc new-edn module-name (assoc info :requires reqs))))

    {}
    edn))

(defn check-presence-of-modules
  [db edn]
  (reduce-kv
    (fn [state module-name {:keys [provider
                                   version
                                   target-platform]
                            :as info}]
      (let [mods (jdbc/execute! db ["SELECT id
                                      FROM module
                                      WHERE name = ?
                                        AND version = ?
                                        AND target_platform = ?
                                        AND (provider_id IS NULL OR provider_id IN (
                                          SELECT id
                                          FROM provider
                                          WHERE name=?
                                        ))"
                                    (str module-name)
                                    version
                                    (or target-platform "universal")
                                    provider])]
        (cond
          (empty? mods)
          (update state :none-found conj module-name)

          (> (count mods) 1)
          (update state :not-unique conj module-name)

          :else
          (update state :exactly-one-found conj module-name))))

    {:none-found []
     :not-unique []
     :exactly-one-found []}
    edn))

(defn ensure-all-modules-unique
  [db edn]
  (let [{:keys [none-found
                not-unique]} (check-presence-of-modules db edn)]
    (if (or (seq none-found)
            (seq not-unique))
      (do
        (when (seq none-found)
          (doseq [module none-found]
            (println "Not Found:" module)))
        (when (seq not-unique)
          (doseq [module not-unique]
            (println "Not Unique:" module)))

        false)
      true)))

(defn missing-requires
  [enriched-edn]
  (set/difference (->> (vals enriched-edn)
                       (mapcat :requires)
                       (map :module_requires/module)
                       (set))
                  (->> (keys enriched-edn)
                       (map name)
                       (set))))

(defn cls
  []
  (println "\033[H\033[2J"))

(defn list-all-modules
  [db edn]
  (let [uniqueness-result (ensure-all-modules-unique db edn)]
    (cls)
    (println uniqueness-result)))

(defn interactive-procure
  []
  (let [edn (init-jdk-edn!)
        db  (rep/from-file "modules.db")
        edn (enrich db edn)]
    (ensure-all-modules-unique db edn)
    (loop []
      (do
        (println (-> (TerminalStyle/builder)
                     (TerminalStyle$Builder/.bold)
                     (TerminalStyle$Builder/.backgroundColor ANSIColor/RED)
                     (.apply "JVM Version Manager (JVM)")))
        (println (String/.stripLeading
                   (String/.stripIndent
                     "
                    -------------------
                    1: List All Modules
                    2: List All Providers
                    3: Add Single Module
                    4: Add Set of Modules
                    "))))
      (loop []
        (print "$: ")
        (let [choice (do (flush)
                         (read-line))]
          (condp = choice
            "1" (list-all-modules db edn)
            "2" (println "unimplemented 1")
            "3" (println "unimplemented 2")
            (println "unimplemented 3"))
          (recur)))
      (recur))
    edn))

(defn -main
  [& args]
  (interactive-procure))
