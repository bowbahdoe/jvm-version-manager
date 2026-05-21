(ns dev.mccue.module-set.routes
  (:require [cheshire.core :as cheshire]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.page.helpers :as page-helpers]
            [dev.mccue.middleware :as middleware]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [reitit.ring.middleware.parameters :as reitit-parameters])
  (:import (java.util UUID)
           (com.fasterxml.uuid Generators)))

(defn module-set-editor
  [db request]
  (let [module-set-id   (UUID/fromString (get (:path-params request) :module_set_id))
        module-set-info (jsonquery/execute!
                          db
                          {:select [:id
                                    [:elements
                                     {:select
                                      [:id
                                       ^:single
                                       [:module
                                        {:select
                                         [:id
                                          :name
                                          :version
                                          :target_platform
                                          [:requires {:select [:id
                                                               :module
                                                               :static
                                                               :transitive
                                                               :mandated
                                                               :version]
                                                      :from :repository.module_requires
                                                      :join-on [:id :module_id]}]]
                                         :from :repository.module
                                         :join-on [:module_id :id]}]]
                                      :from :repository.module_set_element
                                      :join-on [:id :module_set_id]}]]
                           :from :repository.module_set
                           :where [:= :repository.module_set.id module-set-id]})]
    [:code [:pre (cheshire/generate-string
                   module-set-info
                   {:pretty true})]]))

(defn get-module-set-editor
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "Module Set Editor"
    :body (module-set-editor db request)))

(defn post-module-set-create
  [{:system/keys [db]} request]
  (jdbc/execute! db (sql/format
                      {:insert-into :repository.module_set
                       :columns []})))

(defn get-module-set-create
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "Module Set Editor"
    :body [:code [:pre (with-out-str
                         (clojure.pprint/pprint request))]]))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/module_set/create"
     {:get   {:handler    (partial #'get-module-set-create system)}
      :post  {:handler    (partial #'post-module-set-create system)}}]

    ["/module_set/:module_set_id/editor"
     {:get {:handler    (partial #'get-module-set-editor system)}}]]])

(comment
  (let [mccue-sets    (jdbc/execute! (user/db)
                                     (sql/format
                                       {:select [:id :name]
                                        :from :repository.module
                                        :where [:ilike :name "%dev.mccue%"]}))
        module-set-id  (.generate (Generators/timeBasedEpochGenerator))]
    (jdbc/with-transaction [t (user/db)]
      (jdbc/execute! t (sql/format
                         {:insert-into :repository.module_set
                          :columns [:id]
                          :values [[module-set-id]]}))
      (jdbc/execute! t (sql/format
                         {:insert-into :repository.module_set_element
                          :columns [:module_set_id :module_id]
                          :values (mapv (juxt (constantly module-set-id) :module/id)
                                        mccue-sets)})))))
