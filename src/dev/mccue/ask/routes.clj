(ns dev.mccue.ask.routes
  (:require [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]
            [dev.mccue.sidebar.components :as sidebar-components]
            [hiccup2.core :as hiccup]
            [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [ring.util.anti-forgery :as anti-forgery]
            [ring.util.response :as response]))

(defn get-ask-handler
  [{:system/keys [db]} request]
  (let [tags (jdbc/execute!
               db
               (sql/format
                 {:select [:value]
                  :from   :qa.tag}))]
    (page-helpers/page-response
      :title "Ask a Question"
      :body (sidebar-components/sidebar
              request
              [:div {:class (classes ["flex" "flex-col" "content-center" "w-full"])}
               [:p {:class (classes ["flex" "justify-center" "text-lg" "outline-4" "p-4" "m-4" "w-fit"])}
                "Ask a new question"]
               [:form {:method "POST"
                       :action "/ask"
                       :class (classes ["flex" "flex-col" "p-4"])}
                (hiccup/raw (anti-forgery/anti-forgery-field))
                [:label {:class (classes ["text-xl"])
                         :for "title"} "Title"]
                [:input {:type "text"
                         :id "title"
                         :name "title"
                         :class (classes ["outline-3" "outline-rd"
                                          "text-xl"
                                          "width-20"])}]

                [:label {:class (classes ["text-xl"])
                         :for "message"} "Message"]
                [:input {:type "text"
                         :id "message"
                         :name "message"
                         :class (classes ["outline-3" "outline-rd"
                                          "text-xl"
                                          "width-20"])}]
                [:p "Tags"]
                (for [{:tag/keys [value]} tags
                      :let [input-id (str "tag-" value)]]
                  (list
                    [:label {:for input-id} value]
                    [:input {:type "radio" :id input-id :name input-id}]))
                [:input {:type "submit"}]]
               [:div {:class (classes ["outline-2" "outline-dashed" "outline-gray-600" "m-2"])}]
               [:p {:class (classes ["flex" "justify-center" "text-lg" "outline-4" "p-4" "m-4" "w-fit"])}
                "Your questions"]
               [:p (:flash request)]]))))

(defn post-ask-handler
  [system request]
  (let [{:keys [form-params]} request]
    (-> (response/redirect "/ask")
        (assoc :flash (str form-params)))))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/ask" {:get (partial #'get-ask-handler system)
            :post (partial #'post-ask-handler system)}]])
