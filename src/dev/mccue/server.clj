(ns dev.mccue.server
  (:require [reitit.ring :as reitit-ring]
            [dev.mccue.page.routes :as page-routes]
            [dev.mccue.repository.routes :as repository-routes]))

(defn handler
  [system request]
  ((reitit-ring/ring-handler
     (reitit-ring/router
       ["" #_{:middleware [reitit-exception/exception-middleware]}
        [(page-routes/routes system)
         (repository-routes/routes system)]]))
   request))