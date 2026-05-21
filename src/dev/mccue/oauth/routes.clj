(ns dev.mccue.oauth.routes
  (:require [cheshire.core :as cheshire]
            [clojure.tools.logging :as log]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]
            [ring.middleware.oauth2 :as oauth2 :refer [wrap-oauth2]]
            [clj-http.client :as clj-http-client]
            [ring.util.response :as response]))

(defn dummy-route-handler
  [request]
  (log/error
    "Dummy Route Handler Called. Should have been intercepted by oauth middleware. {}"
    request)
  {:status 500
   :body "Internal Error"})


(defn get-github-landing-handler
  [_ request]
  (let [{:keys [token]} (:github (::oauth2/access-tokens (:session request)))]
    (let [response (clj-http-client/get "https://api.github.com/user"
                                         {:headers {"Authorization" (str "Bearer " token)}})]
      (println (get (cheshire/parse-string (:body response)) "login"))
      (-> (response/redirect "/")
          (assoc :session (-> (:session request)
                              (dissoc ::oauth2/access-tokens)
                              (assoc :github/username (get (cheshire/parse-string (:body response)) "login"))))))))

(defn get-logout-handler
  [_ _]
  (-> (response/redirect "/")
      (assoc :session nil)))


(defn routes
  [system]
  (let [github-launch-uri   "/oauth2/github"
        github-redirect-uri "/oauth2/github/callback"
        github-landing-uri  "/oauth2/github/landing"]
    ["" {:middleware (conj (middleware/standard-html-route-middleware system)
                           #(wrap-oauth2 % {:github
                                            {:authorize-uri    "https://github.com/login/oauth/authorize"
                                             :access-token-uri "https://github.com/login/oauth/access_token"
                                             :client-id        (System/getenv "GITHUB_CLIENT_ID")
                                             :client-secret    (System/getenv "GITHUB_CLIENT_SECRET")
                                             :scopes           ["user:email"]
                                             :launch-uri       github-launch-uri
                                             :redirect-uri     github-redirect-uri
                                             :landing-uri      github-landing-uri}}))}
     [[github-launch-uri   {:get #'dummy-route-handler}]
      [github-redirect-uri {:get #'dummy-route-handler}]
      [github-landing-uri  {:get (partial #'get-github-landing-handler system)}]
      ["/logout"           {:get (partial #'get-logout-handler system)}]]]))
