(ns dev.mccue.middleware
  (:require [cheshire.core :as json]
            [clojure.tools.logging :as log]
            [dev.mccue.environment :as environment]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.default-charset :refer [wrap-default-charset]]
            [ring.middleware.flash :refer [wrap-flash]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.x-headers :as x]
            [ring.util.response :as response])
  (:import (io.github.bucket4j Bucket)))

(defn log-request-middleware
  [handler]
  (fn [request]
    (let [response (handler request)]
      (log/info (str (:request-method request) " " (:uri request) " - " (:status response)))
      response)))

(defn authenticate-user-middleware
  [db]
  (fn authenticate-user-middleware
    [handler]
    (fn [request]
      (or (when-let [user_id (:user_id (:session request))]
            (when-let [user-info (jdbc/execute-one! db (sql/format
                                                         {:select [:id :atproto_did]
                                                          :from   :identity.user
                                                          :where  [:= :id (parse-uuid user_id)]}))]
              (handler (assoc request :identity user-info))))
          (handler request)))))

(defn standard-html-route-middleware
  [{:system/keys [session-store db]}]
  [;; Prevents "media type confusion" attacks
   #(x/wrap-content-type-options % :nosniff)
   ;; Prevents "clickjacking" attacks
   #(x/wrap-frame-options % :sameorigin)
   ;; Returns "304 Not Modified" if appropriate
   wrap-not-modified
   ;; Adds "; charset=utf-8" to responses if none specified
   #(wrap-default-charset % "utf-8")
   ;; Guesses an appropriate Content-Type if none set
   wrap-content-type
   ;; Parses out cookies from the request
   wrap-cookies
   ;; Parses out urlencoded form and url parameters
   wrap-params
   ;; Parses out multipart params.
   ;; Useful for things like file uploads
   wrap-multipart-params
   ;; Handles "multi-value" form parameters
   wrap-nested-params
   ;; Turns any string keys in :params into keywords
   wrap-keyword-params
   ;; Handles reading and writing "session data"
   #(wrap-session % {:cookie-attrs {:http-only true
                                    :same-site :lax
                                    :secure    (environment/production?)}
                     :store        session-store})
   ;; Handles "flash" data which is around only until the
   ;; immediate next request.
   wrap-flash
   ;; Ensures that POST requests contain an anti-forgery token
   wrap-anti-forgery
   ;; If we have a user_id in the session, confirm it is valid
   ;; and put a :user key in the request
   (authenticate-user-middleware db)])

(defn require-authenticated-user-middleware
  [{:system/keys [db]}]
  (fn require-authenticated-user-middleware
    [handler]
    (fn [request]
      (if (:identity request)
        (handler request)
        (response/redirect "/login")))))

(defn standard-authenticated-html-route-middleware
  [system]
  (vec (concat (standard-html-route-middleware system)
               [(require-authenticated-user-middleware system)])))

(defn rate-limit-requests-middleware
  [{:system/keys [api-rate-limiter]}]
  (fn rate-limit-requests-middleware
    [handler]
    (fn rate-limited-handler [request]
      (if (Bucket/.tryConsume api-rate-limiter 1)
        (handler request)
        {:status 429
         :body   (json/generate-string {:error "Too Many Requests"})}))))

(defn standard-api-route-middleware
  [system]
  [(rate-limit-requests-middleware system)
   ;; Parses out urlencoded form and url parameters
   wrap-params
   ;; Adds "; charset=utf-8" to responses if none specified
   #(wrap-default-charset % "utf-8")
   ;; Parses out multipart params.
   ;; Useful for things like file uploads
   wrap-multipart-params
   ;; Handles "multi-value" form parameters
   wrap-nested-params
   ;; Turns any string keys in :params into keywords
   wrap-keyword-params])

