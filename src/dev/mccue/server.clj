(ns dev.mccue.server
  (:require [clojure.tools.logging :as log]
            [dev.mccue.environment :as environment]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.module-set.routes :as module-set-routes]
            [dev.mccue.auth.routes :as auth-routes]
            [dev.mccue.chat.routes :as chat-routes]
            [dev.mccue.index.routes :as index-routes]
            [dev.mccue.page.helpers :as page-helpers]
            [dev.mccue.page.routes :as page-routes]
            [dev.mccue.profile.routes :as profile-routes]
            [dev.mccue.qa.routes :as qa-routes]
            [dev.mccue.repository.routes :as repository-routes]
            [hiccup2.core :as hiccup]
            [reitit.ring :as reitit-ring])
  (:import (java.io PrintWriter StringWriter)))


(defn handler
  ([system]
   (let [handler' (reitit-ring/ring-handler
                    (reitit-ring/router
                      ["" {:middleware [middleware/log-request-middleware]}
                       [(module-set-routes/routes system)
                        (auth-routes/routes system)
                        (chat-routes/routes system)
                        (index-routes/routes system)
                        (page-routes/routes system)
                        (profile-routes/routes system)
                        (qa-routes/routes system)
                        (repository-routes/routes system)]])
                    #'page-helpers/_404-page-response)]
     (fn request-handler
       [request]
       (try
         (handler' request)
         (catch Throwable t
           (log/error t "unhandled exception")
           (if (environment/development?)
             {:status 500
              :headers {"Content-Type" "text/html"}
              :body (str
                      (hiccup/html
                        [:code
                         [:pre
                          (let [sw (StringWriter.)]
                            (.printStackTrace t (PrintWriter. sw))
                            (.toString sw))]]))}
             (page-helpers/_500-page-response)))))))

  ([system request]
   ((handler system) request)))
