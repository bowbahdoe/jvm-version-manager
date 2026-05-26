(ns dev.mccue.import.routes
  (:require [cheshire.core :as cheshire]
            [clj-http.client :as clj-http-client]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]
            [dev.mccue.sidebar.components :as sidebar-components]
            [dev.mccue.page.helpers :refer [classes]]
            [hiccup2.core :as hiccup])
  (:import (java.net URI)))

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
             [:input {:class (classes ["text-md" "outline-2" "p-2" "m-2"])
                      :type "search"
                      :name "query"
                      :id "query"
                      :hx-get "/import/maven-central-search"
                      :hx-target "#maven-central-search-results"
                      :hx-swap "innerHTML"
                      :hx-trigger "keyup changed delay:250ms"
                      :placeholder "Search"}]
             [:div {:id "maven-central-search-results"
                    :class (classes ["outline-2"])}
              [:p "Type above to search"]]])))

(defn get-import-maven-central-search-handler
  [system request]
  (let [result (-> (clj-http-client/get
                     "https://search.maven.org/solrsearch/select"
                     {:query-params {"q" (:query (:params request))
                                     "wt" "json"}})
                   (:body)
                   (cheshire/parse-string))]
    #_#_(println (repeat 80 "-"))
    (clojure.pprint/pprint result)

    {:status 200
     :body (str
             (hiccup/html
               [:div {:class (classes ["flex" "flex-col" "w-full"])}
                (for [artifact (get-in result ["response" "docs"])]
                  #_[:code [:pre
                            (with-out-str
                              (clojure.pprint/pprint artifact))]]
                  [:div {:class (classes ["p-3" "m-3" "outline-2" "w-fit" "flex" "flex-col" "spacing-3" "w-full"])}
                   [:div [:a
                          (get artifact "g") ":" (get artifact "a")]]
                   [:div

                    [:a {:class (classes ["rounded-md"
                                          "bg-black"
                                          "text-white"])
                         :href (str "https://central.sonatype.com/artifact/"
                                    (get artifact "g")
                                    "/"
                                    (get artifact "a"))
                         :target "_blank"}
                     "Open in New Tab"]]])]))}))



(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/import" {:get (partial #'get-import-handler system)}]
   ["/import/maven-central-search" {:get (partial #'get-import-maven-central-search-handler system)}]])