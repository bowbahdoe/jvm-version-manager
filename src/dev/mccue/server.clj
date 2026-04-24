(ns dev.mccue.server
  (:require [clojure.java.io :as io]
            [clojure.string :as string]
            [ring.adapter.jetty :as jetty]
            [dev.mccue.repository :as repository]
            [next.jdbc :as jdbc]
            [hiccup2.core :as hiccup]
            [clojure.pprint :as pprint]
            [cheshire.core :as cheshire]
            [reitit.ring :as reitit-ring]
            [reitit.ring.middleware.exception :as reitit-exception]
            [reitit.ring.middleware.parameters :as reitit-parameters]
            [honey.sql :as h])
  (:import (java.lang.module ModuleDescriptor$Version)))

(defn hiccup-response
  [& {:keys [body status]}]
  {:status  (or status 200)
   :headers {"Content-Type" "text/html"}
   :body    (str (hiccup/html body))})

(def solid-box
  ["margin: 4px"
   "border: 2px solid black"
   "border-radius: 2px"
   "width: fit-content"

   "height: fit-content"
   "padding: 4px"])

(def dashed-box
  ["margin: 4px"
   "border: 2px dashed black"
   "border-radius: 2px"
   "width: fit-content"
   "height: fit-content"
   "padding: 4px"])

(defn css
  [classes]
  (string/join ";" classes))

(defn scittle-script
  [& code]
  [:script {:type "application/x-scittle"}
   (hiccup/raw
     (string/join "\n"
                  (for [form code]
                    (with-out-str (pprint/pprint form)))))])

(defn page-response
  [& {:keys [title head body status]}]
  (hiccup-response
    :status status
    :body   [:html {:lang "en"}
             [:head
              (list
                [:script {:src "/htmx.js"}]
                [:script {:src "/alpine.js"}]
                [:title title]
                head)]
             [:body {:style "font-family: monospace"} body]]))

(defn index-handler
  [{:system/keys [db active-module-set-atom]
    :as system}
   request]
  (try
    (let [active-module-set @active-module-set-atom
          selected-modules  (keys active-module-set)]
      (cond
        (empty? selected-modules)
        (page-response
          :body [:h1 "No modules selected yet"])

        :else
        (page-response
          :head (list  [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.js"}]
                       [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.promesa.js"}]
                       [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.js-interop.js"}]
                       (scittle-script
                         '(println "Hello")

                         '(require (quote [promesa.core :as p]))

                         '(p/let [x 5]
                                 (js/alert x))))
          :body
          (list
            (doall
              (for [[name {:keys [version]}] active-module-set]
                [:div {:style (css solid-box)}
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
                     [:p {:style (css solid-box)}
                      (string/join ", " (sort (map :module/target_platform
                                                   (jdbc/execute! db
                                                                  ["SELECT module.target_platform
                                                                    FROM module WHERE name=?"
                                                                   name]))))]
                     [:p "Requires " [:span {:style "font: bold"} "^"]]
                     (for [{:module_requires/keys [module]} rows]
                       [:div {:style (css (conj solid-box "margin-left: 20px"))}
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
       [:input {:id "module-name-input"
                :name "module-name-input"
                :type "text"
                :hx-swap "innerHTML"
                :hx-trigger "keyup changed delay:250ms"
                :hx-target "#search-results"
                :hx-post "/search"}]
       [:br]
       [:label {:for "provides-service-input"} "Provides Service"]
       [:br]
       [:input {:id "provides-service-input"
                :name "provides-service-input"
                :type "text"
                :hx-swap "innerHTML"
                :hx-trigger "keyup changed delay:250ms"
                :hx-target "#search-results"
                :hx-post "/search"}]
       [:br]
       [:input {:id "windows-amd64"
                :type "checkbox"}]


       [:div {:id "search-results"}]])))

(defn post-search-handler
  [{:system/keys [db]} request]
  (let [query   (h/format
                  {:select [:module/id :module/name]
                   :from :module
                   :where [:or
                           (if-let [module-name (get (:params request) "module-name-input")]
                             [:like :module/name (str "%" module-name "%")]
                             [:= 1 0])]})
        _ (println query)
        modules (jdbc/execute! db query)]
    (hiccup-response
      :status 200
      :body [:ul
             (for [module modules]
               [:li {:id (:module/id module)}
                [:a {:style (css ["color: black"])
                     :href (str "/module/" (:module/name module))}
                 [:div {:style (css (conj solid-box "margin-left: 20px"))}
                  (:module/name module)]]])])))

(def htmx-js (slurp (io/resource "htmx.js")))

(defn htmx-handler
  [_ _]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body htmx-js})

(def alpine-js (slurp (io/resource "alpine.js")))

(defn alpine-handler
  [_ _]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body alpine-js})

(defn module-details-handler
  [{:system/keys [db]} request]
  (let [name (get (:path-params request) :name)
        modules (jdbc/execute! db ["SELECT module.*
                                    FROM module
                                    WHERE module.name = ?" name])
        latest-version (first
                         (reverse
                           (sort (map (fn [module]
                                        (ModuleDescriptor$Version/parse (:module/version module)))
                                      modules))))
        latest-modules (filter (fn [module]
                                 (= (ModuleDescriptor$Version/parse (:module/version module))
                                    latest-version))
                               modules)
        enriched       (for [module latest-modules]
                         (assoc module
                           :requires (jdbc/execute! db ["SELECT module_requires.*
                                                         FROM module_requires
                                                         WHERE module_requires.module_id = ?
                                                         ORDER BY module_requires.module ASC"
                                                        (:module/id module)])
                           :exports        (jdbc/execute! db ["SELECT module_exports.*
                                                               FROM module_exports
                                                               WHERE module_exports.module_id = ?
                                                               ORDER BY module_exports.package ASC"
                                                              (:module/id module)])
                           :provides       (jdbc/execute! db ["SELECT module_provides.*
                                                               FROM module_provides
                                                               WHERE module_provides.module_id = ?
                                                               ORDER BY module_provides.service ASC"
                                                              (:module/id module)])
                           :uses           (jdbc/execute! db ["SELECT module_uses.*
                                                               FROM module_uses
                                                               WHERE module_uses.module_id = ?"
                                                              (:module/id module)])))
        by-platform (group-by :module/target_platform enriched)]
    (page-response
      :body
      (for [[_ [to-render]] by-platform]
        (list
          [:div {:style (css ["padding: 20px"
                              "display: flex"
                              "justify-content: center"])}

           [:div {:style (css ["max-width: 800px"
                               "width: 100%"])}

            [:div {:style (css (concat solid-box
                                       ["width: 100%"
                                        "margin-bottom: 16px"]))}
             [:h1 {:style "margin: 0"} (:module/name to-render)]

             [:div {:style (css ["display: flex"
                                 "gap: 10px"
                                 "margin-top: 8px"])}
              [:span {:style (css dashed-box)}
               (str "Version: " (:module/version to-render))]
              [:span {:style (css dashed-box)}
               (str "Platform: " (:module/target_platform to-render))]]]

            (list
              (when (seq (:requires to-render))
                [:div {:style (css (concat solid-box
                                           ["width: 100%"
                                            "margin-bottom: 12px"]))
                       :x-data "{ open: false }"}
                 [:h2 {:style (css ["margin-top: 0"
                                    "margin-bottom: 0"])
                       "@click" "open = !open"} "Requires"
                  [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
                  [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
                 [:ul {:style "padding-left: 20px"
                       :x-show "open"}
                  (for [item (:requires to-render)]
                    [:li
                     [:a {:style (css ["color: black"])
                          :href (str "/module/" (:module_requires/module item))}
                      (:module_requires/module item)]])]])

              (when (seq (:exports to-render))
                [:div {:style (css (concat solid-box
                                           ["width: 100%"
                                            "margin-bottom: 12px"]))
                       :x-data "{ open: false }"}
                 [:h2 {:style (css ["margin-top: 0"
                                    "margin-bottom: 0"])
                       "@click" "open = !open"} "Exports"
                  [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
                  [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
                 [:ul {:style "padding-left: 20px"
                       :x-show "open"}
                  (for [item (:exports to-render)
                        :when (empty? (:module_exports/to item))]
                    [:li {:style "margin: 4px 0"}
                     (:module_exports/package item)])]])

              (when (seq (:provides to-render))
                [:div {:style (css (concat solid-box
                                           ["width: 100%"
                                            "margin-bottom: 12px"]))
                       :x-data "{ open: false }"}
                 [:h2 {:style (css ["margin-top: 0"
                                    "margin-bottom: 0"])
                       :role "button"
                       "@click" "open = !open"} "Provides"
                      [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
                      [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
                 [:ul {:style "padding-left: 20px"
                       :x-show "open"}
                  (for [item (:provides to-render)]
                    [:li {:style "margin: 4px 0"}
                     (:module_provides/service item)])]])

              (when (seq (:uses to-render))
                [:div {:style (css (concat solid-box
                                           ["width: 100%"
                                            "margin-bottom: 12px"]))
                       :x-data "{ open: false }"}
                 [:h2 {:style (css ["margin-top: 0"
                                    "margin-bottom: 0"])
                       "@click" "open = !open"} "Uses"
                  [:span {:x-show "open"} (hiccup/raw " &#x25BC;")]
                  [:span {:x-show "!open"} (hiccup/raw " &#x25B2;")]]
                 [:ul {:style "padding-left: 20px"
                       :x-show "open"}
                  (for [item (:uses to-render)]
                    [:li {:style "margin: 4px 0"}
                     (:module_uses/service item)])]]))]])))))



(defn handler
  [system request]
  ((reitit-ring/ring-handler
     (reitit-ring/router
       ["" #_{:middleware [reitit-exception/exception-middleware]}
        [["/" {:get {:handler (partial #'index-handler system)}}]
         ["/search" {:get (partial #'get-search-handler system)
                     :post {:handler (partial #'post-search-handler system)
                            :middleware [reitit-parameters/parameters-middleware]}}]
         ["/module/:name" {:get (partial #'module-details-handler system)}]
         ["/api/publish" {:post {:handler (partial #'publish-handler system)}}]
         ["/htmx.js" {:get {:handler (partial #'htmx-handler system)}}]
         ["/alpine.js" {:get {:handler (partial #'alpine-handler system)}}]]]))
   request))




(defn start!
  []
  (let [db                     (repository/from-file "modules.db")
        active-module-set-atom (atom {"java.base"    {:version  "25.0.2"}
                                      "java.desktop" {:version "25.0.2"}
                                      "dev.mccue.tools.jdk" {:version "2025.01.31"}})]
    (jetty/run-jetty
      (partial #'handler {:system/db                     db
                          :system/active-module-set-atom active-module-set-atom})
      {:port 8999
       :join? false})))


(comment
  (def server (start!)))