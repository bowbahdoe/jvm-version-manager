(ns dev.mccue.page.routes
  (:require [clojure.java.io :as io]
            [clj-commons.byte-streams :as bs]))

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

(def favicon (with-open [is (io/input-stream (io/resource "favicon.ico"))]
               (bs/to-byte-array is)))

(defn favicon-handler
  [_ _]
  {:status  200
   :headers {"Content-Type" "image/png"}
   :body    favicon})


(defn routes
  [system]
  ["" #_{:middleware [reitit-exception/exception-middleware]}
   [["/favicon.ico" {:get {:handler (partial #'favicon-handler system)}}]
    ["/htmx.js" {:get {:handler (partial #'htmx-handler system)}}]
    ["/alpine.js" {:get {:handler (partial #'alpine-handler system)}}]
    ["/force-graph.js" {:get {:handler (partial #'force-graph-handler system)}}]]])