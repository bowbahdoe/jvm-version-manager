(ns dev.mccue.jsonquery
  (:require [cheshire.core :as cheshire]
            [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:refer-clojure :exclude [format]))

(defn- ->honeysql-helper
  [query parent-table]
  (let [table (:from query)
        conditions (filterv some?
                            [(when parent-table
                               (when-not (:join-on query)
                                 (throw (IllegalArgumentException. (str
                                                                     "Missing :join-on for "
                                                                     parent-table " and " table))))
                               [:=
                                (keyword (str (name parent-table)
                                              "."
                                              (name (first (:join-on query)))))
                                (keyword (str (name table)
                                              "."
                                              (name (second (:join-on query)))))])
                             (:where query)])]

    (cond->
      {:select [[(vec
                  (cons :json_build_object
                        (mapcat
                          (fn [selection]
                            (if (keyword? selection)
                              [[:raw (str "'" (name selection) "'")]
                               (keyword (str (name table) "." (name selection)))]
                              (if (:single (meta selection))
                                [[:raw (str "'" (name (first selection)) "'")]
                                 (->honeysql-helper (second selection) table)]
                                [[:raw (str "'" (name (first selection)) "'")]
                                 [[:array (->honeysql-helper (second selection) table)]]])))
                          (:select query))))]]
       :from table}
      (seq conditions) (assoc :where conditions)
      (:limit query) (assoc :limit (:limit query))
      (:order-by query) (assoc :order-by (:order-by query)))))

(defn ->honeysql
  [query]
  (->honeysql-helper query nil))

(defn format
  [query]
  (sql/format query {:quoted true}))

(defn execute!
  [db query]
  (mapv (comp #(cheshire/parse-string % keyword) str :json_build_object)
        (jdbc/execute!
          db
          (format
            (->honeysql query)))))

(defn execute-one!
  [db query]
  ((comp #(cheshire/parse-string % keyword) str :json_build_object)
   (jdbc/execute-one!
     db
     (format
       (->honeysql query)))))



(comment
  (sql/format
    {:select [[[:json_build_object
                [:raw "'name'"] :repository.module.name]]]
     :from :repository.module})

  (->honeysql
    {:select [:name]
     :from :repository.module})
  (->honeysql
    {:select [:name
              [:packages {:select [:package]
                          :from :repository.module_exports
                          :join-on [:id :module_id]}]]
     :from :repository.module})

  (sql/format
    (->honeysql
      {:select [:name
                [:packages {:select [:package]
                            :from :repository.module_exports
                            :join-on [:id :module_id]}]]
       :from :repository.module}))

  (execute! (user/db)
            {:select [:name
                      :version
                      [:exports {:select [:package]
                                 :from :repository.module_exports
                                 :join-on [:id :module_id]}]
                      [:requires {:select [:module]
                                  :from :repository.module_requires
                                  :join-on [:id :module_id]}]]
             :from :repository.module
             :limit 5})

  (execute! (user/db)
            {:select [:name
                      :version
                      [:exports {:select [:package]
                                 :from :repository.module_exports
                                 :join-on [:id :module_id]}]
                      [:requires {:select [:module]
                                  :from :repository.module_requires
                                  :join-on [:id :module_id]}]]
             :from :repository.module
             :where [:= :repository.module.name "bayern.steinbrecher.ScreenSwitcher"]
             :limit 5})
  (execute! (user/db)
          {:select [:name
                    :version
                    [:exports {:select [:package]
                               :from :repository.module_exports
                               :join-on [:id :module_id]
                               :order-by [[:repository.module_exports.package :desc]]}]
                    [:requires {:select [:module]
                                :from :repository.module_requires
                                :join-on [:id :module_id]}]]
           :from :repository.module
           :order-by [[:repository.module.name :desc]]
           :limit 5})

  (execute! (user/db)
            {:select [:name
                        :version
                        ^:single [:exports {:select [:package]
                                            :from :repository.module_exports
                                            :join-on [:id :module_id]}]
                        [:requires {:select [:module]
                                    :from :repository.module_requires
                                    :join-on [:id :module_id]}]]
               :from :repository.module
               :where [:= :repository.module.name "bayern.steinbrecher.ScreenSwitcher"]
               :limit 5})


  (execute! (user/db)
            {:select [:name]
             :from :repository.module})


  (execute! (user/db)
            {:select [:name
                      [:packages {:select [:package]
                                  :from :repository.module_exports
                                  :join-on [:id :module_id]}]]
             :from :repository.module}))






