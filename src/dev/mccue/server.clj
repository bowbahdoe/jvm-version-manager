(ns dev.mccue.server
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.module-set.routes :as module-set-routes]
            [dev.mccue.auth.routes :as oauth-routes]
            [dev.mccue.page.routes :as page-routes]
            [dev.mccue.qa.routes :as qa-routes]
            [dev.mccue.register.routes :as register-routes]
            [dev.mccue.repository.routes :as repository-routes]
            [reitit.ring.middleware.exception :as middleware-exception]
            [hiccup2.core :as hiccup]
            [reitit.ring :as reitit-ring])
  (:import (java.io PrintWriter StringWriter)))


(defn handler
  ([system]
   (let [handler' (reitit-ring/ring-handler
                    (reitit-ring/router
                      ["" {:middleware [middleware/log-request-middleware]}
                       [(module-set-routes/routes system)
                        (oauth-routes/routes system)
                        (page-routes/routes system)
                        (qa-routes/routes system)
                        (repository-routes/routes system)
                        (register-routes/routes system)]])
                    (constantly {:status 404
                                 :body "Not Found"}))]
     (fn request-handler
       [request]
       (try
         (handler' request)
         (catch Exception e
           {:status 500
            :headers {"Content-Type" "text/html"}
            :body (str
                    (hiccup/html
                      [:code
                       [:pre
                        (let [sw (StringWriter.)]
                          (.printStackTrace e (PrintWriter. sw))
                          (.toString sw))]]))})))))

  ([system request]
   ((handler system) request)))
