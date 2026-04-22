(ns dev.mccue.server
  (:require [clojure.string :as string]
            [ring.adapter.jetty :as jetty]
            [dev.mccue.repository :as repository]
            [next.jdbc :as jdbc]
            [hiccup2.core :as hiccup]
            [clojure.pprint :as pprint]
            [cheshire.core :as cheshire]
            [reitit.ring :as reitit-ring]))

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


(defn index-handler
  [{:system/keys [db active-module-set-atom]
    :as system}
   request]
  (try
    (let [active-module-set @active-module-set-atom
          selected-modules  (keys active-module-set)]
      (cond
        (empty? selected-modules)
        (hiccup-response :body [:html
                                [:body
                                 [:h1 "No modules selected yet"]]])

        :else
        (hiccup-response
          :body

          [:html {:lang "en"}
           [:head
            [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.js"}]
            [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.promesa.js"}]
            [:script {:src "https://cdn.jsdelivr.net/npm/scittle@0.8.31/dist/scittle.js-interop.js"}]
            (scittle-script
              '(println "Hello")

              '(require (quote [promesa.core :as p]))

              '(p/let [x 5]
                      (js/alert x)))]


           [:body {:style "font-family: monospace"}
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
                                {:style "color: red"}) module])])))]))]])))

    (catch Exception e
      (Exception/.printStackTrace e)
      (throw e))))

(defn handler
  [system request]
  ((reitit-ring/ring-handler
     (reitit-ring/router
       ["/" {:get {:handler (partial #'index-handler system)}}]))
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