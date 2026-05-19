(ns dev.mccue.page.helpers
  (:require [clojure.string :as string]
            [hiccup2.core :as hiccup]))


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

(defn page-response
  [& {:keys [title head body status]}]
  (hiccup-response
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
