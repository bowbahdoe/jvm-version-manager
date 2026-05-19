(ns dev.mccue.repository.routes
  (:require [clojure.string :as string]
            [next.jdbc :as jdbc]
            [hiccup2.core :as hiccup]
            [clojure.pprint :as pprint]
            [reitit.ring.middleware.exception :as reitit-exception]
            [reitit.ring.middleware.parameters :as reitit-parameters]
            [honey.sql :as h]
            [dev.mccue.page.helpers :as page-helpers])
  (:import (java.lang.module ModuleDescriptor$Version)))

(defn page-response
  [& {:keys [title head body status]}]
  (page-helpers/hiccup-response
    :status status
    :body [:html {:lang "en"}
           [:head
            (list
              [:script {:src "/htmx.js"}]
              [:script {:src "/alpine.js"
                        :defer true}]
              [:title title]
              head)]
           [:body {:style "font-family: monospace"} body]]))

(defn index-handler
  [{:system/keys [db]
    :as          system}
   request]
  (try
    (let [active-module-set {}
          selected-modules (keys active-module-set)]
      (cond
        (empty? selected-modules)
        (page-response
          :body [:h1 "No modules selected yet"])

        :else
        (page-response
          
          :body
          (list
            (doall
              (for [[name {:keys [version]}] active-module-set]
                [:div {:style (page-helpers/css page-helpers/solid-box)}
                 [:h1 name]
                 (let [rows (jdbc/execute! db ["SELECT module_requires.module as module
                                                              FROM module_requires
                                                              JOIN module
                                                                ON module.id = module_requires.module_id
                                                              WHERE module.name = ?
                                                                AND module.version = ?
                                                              GROUP BY module_requires.module"
                                               name version])]
                   (list
                     [:p "Platforms"]
                     [:p {:style (page-helpers/css page-helpers/solid-box)}
                      (string/join ", " (sort (map :module/target_platform
                                                   (jdbc/execute! db
                                                                  ["SELECT module.target_platform
                                                                    FROM module WHERE name=?"
                                                                   name]))))]
                     [:p "Requires " [:span {:style "font: bold"} "^"]]
                     (for [{:module_requires/keys [module]} rows]
                       [:div {:style (page-helpers/css (conj page-helpers/solid-box "margin-left: 20px"))}
                        (list
                          [:p (if (active-module-set module)
                                {}
                                {:style "color: red"}) module])])))]))))))

    (catch Exception e
      (Exception/.printStackTrace e)
      (throw e))))

(defn publish-handler
  [system request]
  (page-response
    :body [:h2 "Unimplemented"]))

(defn get-search-handler
  [system request]
  (page-response
    :body
    (list
      [:div
       {:id "search"}
       [:label {:for "module-name-input"} "Module Name"]
       [:br]
       [:input {:id         "module-name-input"
                :name       "module-name-input"
                :type       "text"
                :hx-swap    "innerHTML"
                :hx-trigger "keyup changed delay:250ms"
                :hx-target  "#search-results"
                :hx-post    "/search"}]
       [:br]
       [:label {:for "provides-service-input"} "Provides Service"]
       [:br]
       [:input {:id         "provides-service-input"
                :name       "provides-service-input"
                :type       "text"
                :hx-swap    "innerHTML"
                :hx-trigger "keyup changed delay:250ms"
                :hx-target  "#search-results"
                :hx-post    "/search"}]
       [:br]
       [:input {:id   "windows-amd64"
                :type "checkbox"}]


       [:div {:id "search-results"}]])))

(defn post-search-handler
  [{:system/keys [db]} request]
  (let [query (h/format
                {:select [:module/id :module/name]
                 :from   :repository.module
                 :where  [:or
                          (if-let [module-name (get (:params request) "module-name-input")]
                            [:like :module/name (str "%" module-name "%")]
                            [:= 1 0])]})
        modules (jdbc/execute! db query)]
    (page-helpers/hiccup-response
      :status 200
      :body [:ul
             (for [module modules]
               [:li {:id (:module/id module)}
                [:a {:style (page-helpers/css ["color: black"])
                     :href  (str "/module/" (:module/name module))}
                 [:div {:style (page-helpers/css (conj page-helpers/solid-box "margin-left: 20px"))}
                  (:module/name module)]]])])))


(defn get-latest-module-version
  [db & {:keys [name provider-id]}]
  (let [modules (jdbc/execute! db (h/format {:select [:module/version]
                                             :from   :repository.module
                                             :where  [:and
                                                      [:= :module/name name]
                                                      [:= :module/provider_id provider-id]]}))
        latest-version (first
                         (reverse
                           (sort (map (fn [module]
                                        (ModuleDescriptor$Version/parse (:module/version module)))
                                      modules))))]
    latest-version))

(defn get-module-info
  [db & {:keys [name version provider-id]}]
  ;; For a given module there might be multiple
  ;; platform specific variants.
  (when-let [modules (seq
                       (jdbc/execute! db (h/format {:select [:module/id
                                                             :module/name
                                                             :module/target_platform
                                                             :module/mandated
                                                             :module/synthetic]
                                                    :from   :repository.module
                                                    :where  [:and
                                                             [:= :module/name name]
                                                             [:= :module/version (str version)]
                                                             [:= :module/provider_id provider-id]]})))]
    (let [module-name (:module/name (first modules))
          requires (->> (jdbc/execute! db (h/format {:select    [:module_requires/* :module/target_platform]
                                                     :from      :repository.module_requires
                                                     :where     [:in :module_requires/module_id (mapv :module/id modules)]
                                                     :left-join [:repository.module [:= :module/id :module_requires/module_id]]}))
                        (group-by :module_requires/module))
          exports (->> (jdbc/execute! db (h/format {:select    [:module_exports/* :module/target_platform]
                                                    :from      :repository.module_exports
                                                    :where     [:in :module_exports/module_id (mapv :module/id modules)]
                                                    :left-join [:repository.module [:= :module/id :module_exports/module_id]]}))
                       (group-by :module_exports/package))
          provides (->> (jdbc/execute! db (h/format {:select    [:module_provides/* :module/target_platform]
                                                     :from      :repository.module_provides
                                                     :where     [:in :module_provides/module_id (mapv :module/id modules)]
                                                     :left-join [:repository.module [:= :module/id :module_provides/module_id]]}))
                        (group-by :module_provides/service))
          uses (->> (jdbc/execute! db (h/format {:select    [:module_uses/* :module/target_platform]
                                                 :from      :repository.module_uses
                                                 :where     [:in :module_uses/module_id (mapv :module/id modules)]
                                                 :left-join [:repository.module [:= :module/id :module_uses/module_id]]}))
                    (group-by :module_uses/service))]
      {:module/name             module-name
       :module/version          version
       :module/target_platforms (sort (set (map :module/target_platform modules)))
       :requires                requires
       :exports                 exports
       :provides                provides
       :uses                    uses})))

(defn module-table
  [db to-render]
  [:div {:style (page-helpers/css ["max-width: 800px"
                                   "width: 100%"])}

   [:div {:style (page-helpers/css (concat page-helpers/solid-box
                                           ["width: 100%"
                                            "margin-bottom: 16px"]))}
    [:h1 {:style "margin: 0"} (:module/name to-render)]

    [:div {:style (page-helpers/css ["display: flex"
                                     "gap: 10px"
                                     "margin-top: 8px"])}
     [:span {:style (page-helpers/css page-helpers/dashed-box)}
      (str "Version: " (:module/version to-render))]
     [:span {:style (page-helpers/css page-helpers/dashed-box)}
      (str "Platforms: " (string/join ", " (:module/target_platforms to-render)))]]]

   (list
     (when (seq (:requires to-render))
       [:div {:style  (page-helpers/css (concat page-helpers/solid-box
                                                ["width: 100%"
                                                 "margin-bottom: 12px"]))
              :x-data "{ open: false }"}
        [:h2 {:style   (page-helpers/css ["margin-top: 0"
                                          "margin-bottom: 0"])
              "@click" "open = !open"} "Requires"
         [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
         [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
        [:ul {:style  "padding-left: 20px"
              :x-show "open"}
         (for [[required-module platform-specific-info] (sort-by first (:requires to-render))]
           [:li
            [:a {:style (page-helpers/css ["color: black"])
                 :href  (str "/module/" required-module)}
             required-module]])]])

     (when (seq (:exports to-render))
       [:div {:style  (page-helpers/css (concat page-helpers/solid-box
                                                ["width: 100%"
                                                 "margin-bottom: 12px"]))
              :x-data "{ open: false }"}
        [:h2 {:style   (page-helpers/css ["margin-top: 0"
                                          "margin-bottom: 0"])
              "@click" "open = !open"} "Exports"
         [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
         [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
        [:ul {:style  "padding-left: 20px"
              :x-show "open"}
         (for [[exported-package platform-specific-info] (sort-by first (:exports to-render))
               :when (not (every? #(seq (:module_exports/to %)) platform-specific-info))]
           [:li {:style "margin: 4px 0"}
            exported-package])]])

     (when (seq (:provides to-render))
       [:div {:style  (page-helpers/css (concat page-helpers/solid-box
                                                ["width: 100%"
                                                 "margin-bottom: 12px"]))
              :x-data "{ open: false }"}
        [:h2 {:style   (page-helpers/css ["margin-top: 0"
                                          "margin-bottom: 0"])
              :role    "button"
              "@click" "open = !open"} "Provides"
         [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
         [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
        [:ul {:style  "padding-left: 20px"
              :x-show "open"}
         (for [[provided-service platform-specific-info] (sort-by first (:provides to-render))]
           (if (= (set (map :module/target_platform platform-specific-info))
                  (set (:module/target_platforms to-render)))
             [:li {:style "margin: 4px 0"}
              provided-service]
             [:li {:style (page-helpers/css ["margin: 4px 0"
                                             "color: blue"])}
              provided-service]))]])

     (when (seq (:uses to-render))
       [:div {:style  (page-helpers/css (concat page-helpers/solid-box
                                                ["width: 100%"
                                                 "margin-bottom: 12px"]))
              :x-data "{ open: false }"}
        [:h2 {:style   (page-helpers/css ["margin-top: 0"
                                          "margin-bottom: 0"])
              "@click" "open = !open"} "Uses"
         [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
         [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
        [:ul {:style  "padding-left: 20px"
              :x-show "open"}
         (for [[used-service platform-specific-info] (sort-by first (:uses to-render))]
           [:li {:style "margin: 4px 0"}
            used-service])]]))])

(defn module-details-handler
  [{:system/keys [db]} request]
  (let [name (get (:path-params request) :name)
        provider-id (get (:query-params request) "provider-id")]
    (when-let [version (or (get (:query-params request) "version")
                           (get-latest-module-version db
                                                      :name name
                                                      :provider-id provider-id))]
      (let [to-render (get-module-info db
                                       :name name
                                       :version version
                                       :provider-id provider-id)]
        (page-response
          :head
          [:script {:src "/force-graph.js"}]
          :body
          (list
            [:div {:style (page-helpers/css ["padding: 20px"
                                             "display: flex"
                                             "justify-content: center"])}
             (module-table db to-render)]))))))

(defn module-set-details-handler
  [{:system/keys [db]} request]
  (let [name (get (:path-params request) :name)
        provider-id (get (:query-params request) "provider-id")]
    (when-let [ms (seq (jdbc/execute! db (h/format {:select [:module_set_element/*]
                                                    :from :repository.module_set
                                                    :where [:= :module_set/name name]
                                                    :right-join [:module_set_element [:= :module_set/id :module_set_element/id]]})))]
      (page-response

        :body
        (list
          [:div {:style (page-helpers/css ["padding: 20px"
                                           "display: flex"
                                           "justify-content: center"])}
           (do (pprint/pprint ms)
               "abc")])))))


(defn routes
  [system]
  ["" #_{:middleware [reitit-exception/exception-middleware]}
   [["/" {:get {:handler (partial #'index-handler system)}}]
    ["/search" {:get  (partial #'get-search-handler system)
                :post {:handler    (partial #'post-search-handler system)
                       :middleware [reitit-parameters/parameters-middleware]}}]
    ["/module/:name" {:get        (partial #'module-details-handler system)
                      :middleware [reitit-parameters/parameters-middleware]}]
    ["/module-set/:name" {:get (partial #'module-set-details-handler system)
                          :middleware [reitit-parameters/parameters-middleware]}]
    ["/api/publish" {:post {:handler (partial #'publish-handler system)}}]]])