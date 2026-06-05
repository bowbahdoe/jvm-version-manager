(ns dev.mccue.index.routes
  (:require [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.middleware :as middleware]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (java.io InputStream)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (org.sqlite SQLiteDataSource)))


(defn- sqlite-db
  [path]
  (let [db (doto (SQLiteDataSource.)
             (SQLiteDataSource/.setUrl (str "jdbc:sqlite:" path)))]
    (doseq [command (-> (slurp (io/resource "sqlite-init.sql"))
                        (string/split #"--;"))]
      (jdbc/execute! db [command]))
    db))

(defn get-matching-columns
  [postgres-db sqlite-db table]
  (let [postgres-columns (mapv :columns/column_name
                               (jdbc/execute! postgres-db
                                              (sql/format
                                                {:select [:column_name]
                                                 :from :information_schema.columns
                                                 :where [:and
                                                         [:= :table_schema "repository"]
                                                         [:= :table_name (name table)]]})))
        sqlite-columns   (mapv :name
                               (jdbc/execute! sqlite-db
                                              [(str "pragma table_info(" (name table) ")")]))]

    (mapv keyword
          (sort
            (set/intersection (set (map string/lower-case postgres-columns))
                              (set (map string/lower-case sqlite-columns)))))))


(defn build-index
  [db]
  (let [temp-file (Files/createTempFile "new" ".db" (into-array FileAttribute []))]
    (try
      (jdbc/with-transaction [t db]
        (let [index  (str temp-file)
              new-db (sqlite-db index)

              transfer! (fn [table where]
                          (log/info (str "Transferring " table))
                          (let [columns (get-matching-columns t new-db table)
                                source (jdbc/execute! t
                                                      (sql/format
                                                        {:select (mapv #(keyword (name table) (name %))
                                                                       columns)
                                                         :from (keyword (str "repository." (name table)))
                                                         :where where}
                                                        {:quoted true}))]

                            (when (seq source)
                              (jdbc/execute! new-db (sql/format
                                                      {:insert-into table
                                                       :columns columns
                                                       :values (mapv (apply juxt (mapv #(keyword (name table) (name %))
                                                                                       columns))
                                                                     source)}
                                                      {:quoted true})))))]

          (let [published-module-ids (->> (jdbc/execute!
                                            t
                                            (sql/format
                                              {:select [:module_id]
                                               :from :repository.published_module}))
                                          (map :published_module/module_id))]
            (if-not (seq published-module-ids)
              (log/warn "No published modules")
              (do
                (transfer! :module [:in :id published-module-ids])
                (transfer! :module_provides [:in :module_id published-module-ids])
                (transfer! :module_uses [:in :module_id published-module-ids])
                (transfer! :module_requires [:in :module_id published-module-ids])
                (transfer! :module_exports [:in :module_id published-module-ids])
                (transfer! :module_exports_to (let [exports (->> (jdbc/execute!
                                                                   t
                                                                   (sql/format
                                                                     {:select [:id]
                                                                      :from :repository.module_exports
                                                                      :where [:in :module_id published-module-ids]}))
                                                                 (map :module_exports/id))]
                                                (when (seq exports)
                                                  [:in :module_exports_id exports])))
                (transfer! :module_package [:in :module_id published-module-ids])
                (transfer! :module_hash [:in :module_id published-module-ids])
                (transfer! :published_module [:= 1 1]))))


          (with-open [is (io/input-stream (Path/.toFile temp-file))]
            (InputStream/.readAllBytes is))))
      (finally (Files/deleteIfExists temp-file)))))


(defn get-index
  [{:system/keys [db]} _]
  {:status 200
   :headers {"Content-Type"        "application/octet-stream"
             "Content-Disposition" "attachment; filename=\"index.db\""}
   :body   (build-index db)})

(defn get-module
  [{:system/keys [db]} request]
  (let [matching-module        (jdbc/execute-one!
                                 db
                                 (sql/format
                                   {:select [:name :version :artifact.data]
                                    :from   :repository.module
                                    :left-join [:repository.artifact
                                                [:= :repository.module.cid :repository.artifact.cid]]
                                    :where     [:= :repository.module.cid (:cid (:path-params request))]}))
        {:artifact/keys [data]} matching-module
        {:module/keys [name version]} matching-module]
    (if-not data
      {:status 404
       :body   {:error "Module content not in this repo."}}
      {:status 200
       :headers {"Content-Type"        "application/java-archive"
                 "Content-Disposition" (format "attachment; filename=\"%s\""
                                               (doto (str name
                                                          (when version
                                                            (str "@" version))
                                                          (if (and (= (aget data 0) (byte \J))
                                                                   (= (aget data 1) (byte \M)))
                                                            ".jmod"
                                                            ".jar"))
                                                 (println)))}
       :body   data})))


(defn routes
  [system]
  ["" {:middleware (middleware/standard-api-route-middleware system)}
   ["/index" {:get (partial #'get-index system)}]
   ["/index.db" {:get (partial #'get-index system)}]
   ["/module/:cid" {:get (partial #'get-module system)}]])