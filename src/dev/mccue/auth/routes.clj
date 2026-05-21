(ns dev.mccue.auth.routes
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [dev.mccue.auth.duke :as duke]
            [dev.mccue.environment :as environment]
            [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.middleware.oauth2 :as oauth2 :refer [wrap-oauth2]]
            [clj-http.client :as clj-http-client]
            [ring.util.response :as response])
  (:import (dev.mccue.duke Duke Seed)
           (java.util Hashtable)
           (javax.naming NamingEnumeration)
           (javax.naming.directory DirContext InitialDirContext Attributes Attribute)))


(defn dummy-route-handler
  [request]
  (log/error
    "Dummy Route Handler Called. Should have been intercepted by oauth middleware. {}"
    request)
  {:status 500
   :body "Internal Error"})


(defn get-github-landing-handler
  [{:system/keys [db]} request]
  (let [{:keys [token]} (:github (::oauth2/access-tokens (:session request)))]
    (let [response       (clj-http-client/get "https://api.github.com/user"
                                               {:headers {"Authorization" (str "Bearer " token)}})
          github-user-id (get (cheshire/parse-string (:body response)) "id")]
      (jdbc/execute! db (sql/format
                          {:insert-into :identity.user
                           :columns [:github_user_id :profile_image_png_base64]
                           :values  [[(str github-user-id) (duke/duke->png-base64 (Duke. (Seed. github-user-id)))]]
                           :on-conflict []
                           :do-nothing  true}))
      (let [{:user/keys [id]} (jdbc/execute-one! db (sql/format
                                                      {:select [:id]
                                                       :from :identity.user
                                                       :where [:= :github_user_id (str github-user-id)]}))]
        (-> (response/redirect "/")
            (assoc :session (-> (:session request)
                                (dissoc ::oauth2/access-tokens)
                                (assoc :user_id id))))))))

(defn get-logout-handler
  [_ _]
  (-> (response/redirect "/")
      (assoc :session nil)))

(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body [:div
           [:h1 (str "prod? " (environment/production?))]
           [:h1 (str "Hello: " (:user_id (:session request)))]
           [:a {:href "/oauth2/github"} "Login with GitHub"]
           [:br]
           [:a {:href "/logout"} "Logout"]
           [:ul
            (for [user (jsonquery/execute! db {:select [:id :profile_image_png_base64]
                                               :from   :identity.user})]
              [:li [:img {:src (str "data:image/png;base64, " (:profile_image_png_base64 user))
                          :width 128
                          :height 128
                          :style "image-rendering: pixelated"}]
               [:p (:id user)]])]]))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/"                 {:get (partial #'index-handler system)}]
    ["/logout"           {:get (partial #'get-logout-handler system)}]
    (let [github-launch-uri   "/oauth2/github"
          github-redirect-uri "/oauth2/github/callback"
          github-landing-uri  "/oauth2/github/landing"]
      ["" {:middleware [#(wrap-oauth2 % {:github
                                         {:authorize-uri    "https://github.com/login/oauth/authorize"
                                          :access-token-uri "https://github.com/login/oauth/access_token"
                                          :client-id        (System/getenv "GITHUB_CLIENT_ID")
                                          :client-secret    (System/getenv "GITHUB_CLIENT_SECRET")
                                          :scopes           ["user:email"]
                                          :launch-uri       github-launch-uri
                                          :redirect-uri     github-redirect-uri
                                          :landing-uri      github-landing-uri}})]}
       [[github-launch-uri   {:get #'dummy-route-handler}]
        [github-redirect-uri {:get #'dummy-route-handler}]
        [github-landing-uri  {:get (partial #'get-github-landing-handler system)}]]])]])




(defn get-did
  [atproto-handle]
  (let [dir (InitialDirContext.
              (doto (Hashtable.)
                (.put "java.naming.factory.initial",
                      "com.sun.jndi.dns.DnsContextFactory")))
        attrs (^[String String/1]
                DirContext/.getAttributes
                dir
                (str "_atproto." atproto-handle)
                (into-array String ["TXT"]))
        txt     (Attributes/.get attrs "TXT")
        e       (Attribute/.getAll txt)]
    (loop []
      (when (NamingEnumeration/.hasMore e)
        (let [value (NamingEnumeration/.next e)]
          (if (string/starts-with? value "did=")
            (string/replace-first value "did=" "")
            (recur)))))))

(defn resolve-did
  [did]
  (let [info                 (cheshire/parse-string-strict
                               (slurp (str "https://plc.directory/" did)))
        service-endpoint     (get-in info ["service" 0 "serviceEndpoint"])
        service-info         (cheshire/parse-string-strict
                               (slurp (str service-endpoint "/.well-known/oauth-protected-resource")))
        authorization-server (get-in service-info ["authorization_servers" 0])
        authorization-server-description (cheshire/parse-string-strict
                                           (slurp (str authorization-server
                                                       "/.well-known/oauth-authorization-server")))]
    authorization-server-description))