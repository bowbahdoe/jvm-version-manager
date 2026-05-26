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
    #_#_(println (repeat 80 "-"))
            (clojure.pprint/pprint result)

    {:status 200
     :body   (str
               (hiccup/html
                 [:div {:class (classes ["flex" "flex-col" "w-full"])}
                  (for [artifact (get-in result ["response" "docs"])]
                    #_[:code [:pre
                              (with-out-str
                                (clojure.pprint/pprint artifact))]]
                    [:div {:class (classes ["p-3" "m-3" "outline-2" "flex" "flex-col" "spacing-3"])}
                     [:div [:a
                            (get artifact "g") ":" (get artifact "a")]]
                     [:div
                      [:a {:class  (classes ["rounded-md"
                                             "bg-black"
                                             "text-white"])
                           :href   (str "https://central.sonatype.com/artifact/"
                                        (get artifact "g")
                                        "/"
                                        (get artifact "a"))
                           :target "_blank"}
                       "Open in New Tab"]]
                     [:form {:method "POST"
                             :action "/import/maven"}
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
        user-id (:user/id (:user request))
        digest (MessageDigest/getInstance "sha256")
        sha256 (HexFormat/.formatHex (HexFormat/of)
                                     (MessageDigest/.digest digest (:bytes fetched-artifact)))
        module-id (.generate (Generators/timeBasedEpochGenerator))]
    (jdbc/with-transaction [t db]
      (jdbc/execute! t
                     (sql/format
                       {:insert-into :repository.module
                        :columns     [:id
                                      :name
                                      :version
                                      :target_platform
                                      :mandated
                                      :synthetic
                                      :module_info
                                      :mvn_repository
                                      :mvn_groupId
                                      :mvn_artifactId
                                      :mvn_version
                                      :mvn_classifier
                                      :type
                                      :sha256
                                      :user_id]
                        :values      [[module-id
                                       (:name module-info)
                                       (:version module-info)
                                       (or (:target-platform module-info) "universal")
                                       (or (:mandated module-info) false)
                                       (or (:synthetic module-info) false)
                                       (with-out-str (pprint/pprint module-info))
                                       (:mvn/repository artifact)
                                       (:mvn/groupId artifact)
                                       (:mvn/artifactId artifact)
                                       (:mvn/version artifact)
                                       (:mvn/classifier artifact)
                                       "jar"
                                       sha256
                                       user-id]]}))
      (when true
        (doseq [provides (:provides module-info)]
          (doseq [with (:with provides)]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_provides(
                                 module_id,
                                 service,
                                 \"with\"
                              )
                              VALUES (?, ?, ?)
                              ON CONFLICT DO NOTHING")
                              module-id
                              (:service provides)
                              with])))

        (doseq [uses (:uses module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_uses(
                                   module_id,
                                   service
                                )
                                VALUES (?, ?)
                                ON CONFLICT DO NOTHING")
                            module-id
                            (:service uses)]))
        (doseq [requires (:requires module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_requires(
                                 module_id,
                                 module,
                                 version,
                                 static,
                                 transitive,
                                 mandated,
                                 synthetic
                              )
                              VALUES (?, ?, ?, ?, ?, ?, ?)
                              ON CONFLICT DO NOTHING")
                            module-id
                            (:module requires)
                            (:version requires)
                            (or (:static requires)
                                false)
                            (or (:transitive requires)
                                false)
                            (or (:mandated requires)
                                false)
                            (or (:synthetic requires)
                                false)]))
        (doseq [exports (:exports module-info)]
          (doseq [to (if (seq (:to exports))
                       (:to exports)
                       [nil])]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_exports(
                                   module_id,
                                   package,
                                   \"to\",
                                   mandated,
                                   synthetic
                                )
                                VALUES (?, ?, ?, ?, ?)
                                ON CONFLICT DO NOTHING")
                              module-id
                              (:package exports)
                              to
                              (or (:mandated exports)
                                  false)
                              (or (:synthetic exports)
                                  false)])))

        (doseq [{:keys [package]} (:packages module-info)]
          (jdbc/execute! t [(String/.stripIndent
                              "INSERT INTO repository.module_package(
                                   module_id,
                                   package
                                )
                                VALUES (?, ?)
                                ON CONFLICT DO NOTHING")
                            module-id
                            package]))

        (let [{:keys [algorithm hashes]} (:hashes module-info)]
          (doseq [{:keys [module hash]} hashes]
            (jdbc/execute! t [(String/.stripIndent
                                "INSERT INTO repository.module_hash(
                                     module_id,
                                     module,
                                     algorithm,
                                     hash
                                  )
                                  VALUES (?, ?, ?, ?)
                                  ON CONFLICT DO NOTHING")
                              module-id
                              module
                              algorithm
                              hash])))))
    (-> (response/redirect "/import")
        (assoc :flash (str (:form-params request))))))


(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/import" {:get (partial #'get-import-handler system)}]
   ["/import/maven" {:post (partial #'post-import-maven-handler system)}]
   ["/import/maven-central-search" {:get (partial #'get-import-maven-central-search-handler system)}]])

(comment
  (dev.mccue.jsonquery/execute!
    (user/db)
    {:select [:name
              :version
              :target_platform
              :mandated
              :synthetic
              [:requires {:select [:module
                                   :version
                                   :static
                                   :transitive
                                   :mandated
                                   :synthetic]
                          :from :repository.module_requires
                          :join-on [:id :module_id]}]
              [:exports {:select [:package
                                  :to
                                  :mandated
                                  :synthetic]
                         :from :repository.module_exports
                         :join-on [:id :module_id]}]
              [:uses {:select [:service]
                      :from :repository.module_uses
                      :join-on [:id :module_id]}]
              [:provides {:select [:service
                                   :with]
                          :from :repository.module_provides
                          :join-on [:id :module_id]}]
              [:packages {:select [:package]
                          :from :repository.module_package
                          :join-on [:id :module_id]}]
              [:hashes {:select [:module
                                 :algorithm
                                 :hash]
                        :from :repository.module_hash
                        :join-on [:id :module_id]}]
              ^:single [:user {:select [:id
                                        :atproto_did]
                               :from :identity.user
                               :join-on [:user_id :id]}]]
     :from :repository.module
     :order-by [[:repository.module.name :asc]
                [:repository.module.version :desc]]}))