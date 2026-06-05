(ns dev.mccue.import.routes
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as clj-http-client]
            [clojure.pprint :as pprint]
            [dev.mccue.repository.artifact :as artifact]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]
            [dev.mccue.repository.jmod :as jmod]
            [dev.mccue.repository.repository :as repository]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :refer [classes]]
            [dev.mccue.atproto.cid :as cid]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response])
  (:import (com.fasterxml.uuid Generators)
           (java.net URI)
           (java.security MessageDigest)
           (java.util HexFormat)))

(defn get-import-handler
  [system request]
  (page-helpers/page-response
    :title "Import Libraries"
    :body (sidebar-components/sidebar
            request
            [:div
             [:p {:class (classes ["flex" "justify-center" "text-lg" "outline-4" "p-4" "m-4" "w-fit"])}
              "Import a module from maven central"]
             [:label {:for "query"}
              "Query"]
             [:input {:class       (classes ["text-md" "outline-2" "p-2" "m-2"])
                      :type        "search"
                      :name        "query"
                      :id          "query"
                      :hx-get      "/import/maven-central-search"
                      :hx-target   "#maven-central-search-results"
                      :hx-swap     "innerHTML"
                      :hx-trigger  "keyup changed delay:250ms"
                      :placeholder "Search"}]
             [:div {:id    "maven-central-search-results"
                    :class (classes ["outline-2"])}
              [:p "Type above to search"]]])))

(defn get-import-maven-central-search-handler
  [system request]
  (let [result (-> (clj-http-client/get
                     "https://search.maven.org/solrsearch/select"
                     {:query-params {"q"  (:query (:params request))
                                     "wt" "json"}})
                   (:body)
                   (cheshire/parse-string))]

    {:status 200
     :body   (str
               (hiccup/html
                 [:div {:class (classes ["flex" "flex-col" "w-full"])}
                  (for [artifact (get-in result ["response" "docs"])]
                    [:div {:class (classes ["p-3" "m-3" "outline-2" "flex" "flex-col" "spacing-3" "gap-3" "text-center"])}
                     [:div [:a (get artifact "g") ":" (get artifact "a")]]
                     [:div {:class (classes ["rounded-md"
                                             "bg-black"
                                             "text-white"
                                             "cursor-pointer"
                                             "p-2"])}
                      [:a {:href   (str "https://central.sonatype.com/artifact/"
                                        (get artifact "g")
                                        "/"
                                        (get artifact "a"))
                           :target "_blank"}
                       "Open in New Tab"]]

                     [:form {:method "POST"
                             :action "/import/maven"
                             :class  (classes ["rounded-md"
                                               "bg-black"
                                               "text-white"
                                               "cursor-pointer"
                                               "p-2"])}
                      (hiccup/raw (anti-forgery/anti-forgery-field))
                      [:input {:type  "hidden"
                               :name  "g"
                               :value (get artifact "g")}]
                      [:input {:type  "hidden"
                               :name  "a"
                               :value (get artifact "a")}]
                      [:input {:type  "hidden"
                               :name  "v"
                               :value (get artifact "latestVersion")}]
                      [:input {:type  "submit"
                               :value (str "Download Latest (" (get artifact "latestVersion") ")")}]]])]))}))


(defn post-import-maven-handler
  [{:system/keys [db]} request]
  (let [{:strs [g a v]} (:form-params request)
        artifact (artifact/maven-central-artifact {:groupId    g
                                                   :artifactId a
                                                   :version    v})
        fetched-artifact (artifact/fetch-artifact-cached db artifact)
        module-info (artifact/module-info-from-archive-bytes (:bytes fetched-artifact))
        user-id (:user/id (:identity request))
        cid (cid/bytes->cid-string (:bytes fetched-artifact))
        module-id (.generate (Generators/timeBasedEpochGenerator))]))



(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/import" {:get (partial #'get-import-handler system)}]
   ["/import/maven" {:post (partial #'post-import-maven-handler system)}]
   ["/import/maven-central-search" {:get (partial #'get-import-maven-central-search-handler system)}]])

(comment
  (dev.mccue.jsonquery/execute!
    (user/db)
    {:select   [:name
                :version
                :target_platform
                :mandated
                :synthetic
                [:requires {:select  [:module
                                      :version
                                      :static
                                      :transitive
                                      :mandated
                                      :synthetic]
                            :from    :repository.module_requires
                            :join-on [:id :module_id]}]
                [:exports {:select  [:package
                                     :to
                                     :mandated
                                     :synthetic]
                           :from    :repository.module_exports
                           :join-on [:id :module_id]}]
                [:uses {:select  [:service]
                        :from    :repository.module_uses
                        :join-on [:id :module_id]}]
                [:provides {:select  [:service
                                      :with]
                            :from    :repository.module_provides
                            :join-on [:id :module_id]}]
                [:packages {:select  [:package]
                            :from    :repository.module_package
                            :join-on [:id :module_id]}]
                [:hashes {:select  [:module
                                    :algorithm
                                    :hash]
                          :from    :repository.module_hash
                          :join-on [:id :module_id]}]
                ^:single [:user {:select  [:id
                                           :atproto_did]
                                 :from    :identity.user
                                 :join-on [:user_id :id]}]]
     :from     :repository.module
     :order-by [[:repository.module.name :asc]
                [:repository.module.version :desc]]}))