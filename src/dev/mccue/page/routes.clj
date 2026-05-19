(ns dev.mccue.page.routes
  (:require [clojure.java.io :as io]))

(def htmx-js (slurp (io/resource "htmx.js")))

(defn htmx-handler
  [_ _]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    htmx-js})

(def alpine-js (slurp (io/resource "alpine.js")))

(defn alpine-handler
  [_ _]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    alpine-js})

(def force-graph-js (slurp (io/resource "force-graph.js")))

(defn force-graph-handler
  [_ _]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    force-graph-js})

(defn routes
  [system]
  ["" #_{:middleware [reitit-exception/exception-middleware]}
   [["/htmx.js" {:get {:handler (partial #'htmx-handler system)}}]
    ["/alpine.js" {:get {:handler (partial #'alpine-handler system)}}]
    ["/force-graph.js" {:get {:handler (partial #'force-graph-handler system)}}]]])