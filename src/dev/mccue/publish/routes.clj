(ns dev.mccue.publish.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]))


(defn- all-module-info-partial-query
  []
  [:name
   :version
   :target_platform
   :synthetic
   :mandated
   :open
   [:exports
    {:select [:package
              :mandated
              :synthetic
              [:to {:select [:module]
                    :from :repository.module_exports_to
                    :join-on [:id :module_exports_id]}]]
     :from :repository.module_exports
     :join-on [:id :module_id]}]
   [:requires
    {:select [:module
              :version
              :static
              :transitive
              :mandated
              :synthetic]
     :from :repository.module_requires
     :join-on [:id :module_id]}]
   [:uses
    {:select [:service]
     :from :repository.module_uses
     :join-on [:id :module_id]}]
   [:provides
    {:select [:service :with]
     :from :repository.module_provides
     :join-on [:id :module_id]}]
   [:packages
    {:select [:package]
     :from :repository.module_package
     :join-on [:id :module_id]}]
   [:hashes {:select [:module
                      :algorithm
                      :hash]
             :from :repository.module_hash
             :join-on [:id :module_id]}]])

(comment
  (jsonquery/execute!
    (user/db)
    {:select [:name :version :id]
     :from :repository.module}))

(defn get-modules-belonging-to-user
  [db atproto-did]
  (jsonquery/execute!
    db
    {:select
     [:id
      :rkey
      ^:single
      [:module
       {:select
        [:id
         [:published_module
          {:select [:module_name :module_version :module_info]
           :from :repository.published_module
           :join-on [:id :dev_mccue_jvm_module_id]}]
         [:variants
          {:select
           [:id
            ^:single
            [:module-info
             {:select (all-module-info-partial-query)
              :from :repository.module
              :join-on [:artifact_cid_link :cid]}]]
           :from :atproto.dev_mccue_jvm_module_variant
           :join-on [:id :dev_mccue_jvm_module_id]}]]
        :from :atproto.dev_mccue_jvm_module
        :join-on [:id :atproto_record_id]}]]
     :from :atproto.record
     :where [:and
             [:= :did atproto-did]
             [:= :collection "dev.mccue.jvm.module"]]}))

(comment
  (def did-a "did:plc:dt7fth2hmap6wya7uyyl2g3v")
  (def did-b "did:plc:2oip3ubsbe2pc7tmbnwsm3i7")
  (get-modules-belonging-to-user (user/db) did-a)

  (clojure.pprint/pprint
    (get-modules-belonging-to-user (user/db) did-b)))


(defn get-publish-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "Publish"
    :body (sidebar-components/sidebar
            request
            [:main {:class (classes ["flex" "flex-col" "gap-2" "m-2"])}
             [:p (str (:atproto_did (:identity request)))]
             (for [record (get-modules-belonging-to-user
                            db
                            (:user/atproto_did (:identity request)))]
               (list
                 [:form
                  [:input {:type "submit"}
                   "Publish"]]
                 [:p (:rkey record)]
                 [:p (str (count (get-in record [:module :variants]))
                          " variants")]))
             [:code [:pre (with-out-str
                            (clojure.pprint/pprint (get-modules-belonging-to-user
                                                     db
                                                     (:user/atproto_did (:identity request)))))]]])))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   [["/publish" {:get (partial #'get-publish-handler system)}]]])