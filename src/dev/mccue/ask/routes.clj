(ns dev.mccue.ask.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
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
              [:div {:class (classes ["flex" "flex-col" "content-center" "w-full"])
                     :x-data "{open: true}"}
               [:button {:class (classes ["flex"
                                          "justify-center"
                                          "text-lg" "outline-4" "p-4" "m-4" "w-fit"
                                          "cursor-pointer"
                                          "hover:bg-blue-200"])
                         "@click" "open = !open"}
                [:span {:x-show "open"} "Ask a new question ▼"]
                [:span {:x-show "!open"} "Ask a new question ▲"]]
               [:div {:class (classes ["flex" "flex-col" "content-center" "w-full"])
                      :x-show "open"}
                [:form {:method "POST"
                        :action "/ask"
                        :class (classes ["flex" "flex-col" "p-4"])}
                 (hiccup/raw (anti-forgery/anti-forgery-field))
                 [:label {:class (classes ["text-xl"])
                          :for "title"} "Title"]
                 [:input {:type "text"
                          :id "title"
                          :name "title"
                          :class (classes ["outline-3"
                                           "text-xl"
                                           "p-2"])}]

                 [:label {:class (classes ["text-xl"])
                          :for "message"} "Message"]
                 [:input {:type "text"
                          :id "message"
                          :name "message"
                          :class (classes ["outline-3"
                                           "text-xl"
                                           "p-2"])}]
                 [:p "Tags"]
                 [:div {:class (classes ["flex" "flex-col" "space-1"])}
                  (for [{:tag/keys [value]} tags
                        :let [input-id (str "tag-" value)]]
                    (list
                      [:label {:for input-id
                               :class (classes ["group"])}
                       [:span {:class (classes ["group-has-checked:outline-gray-3" "cursor-pointer"])} value]
                       [:input {:class (classes ["peer"]) :type "checkbox" :id input-id :name input-id}]]))]
                 [:input {:type "submit"}]]]
               [:div {:class (classes ["outline-2" "outline-dashed" "outline-gray-600" "m-2"])}]
               [:p {:class (classes ["flex" "justify-center" "text-lg" "outline-4" "p-4" "m-4" "w-fit"])}
                "Your questions"]
               [:p (:flash request)]
               (for [question (jsonquery/execute! db
                                                  {:select [:title
                                                            :message
                                                            ^:single
                                                            [:asked_by {:select [:id :atproto_did]
                                                                        :from :identity.user
                                                                        :join-on [:asked_by_user_id :id]}]
                                                            :created_at
                                                            :updated_at]
                                                   :from :qa.question})]
                 [:code [:pre (with-out-str (clojure.pprint/pprint question))]])]))))

(defn post-ask-handler
  [{:system/keys [db]} request]
  (let [{:keys [form-params]} request
        {:strs [title message]} form-params]
    (jdbc/execute!
      db
      (sql/format
        {:insert-into :qa.question
         :columns [:title :message :asked_by_user_id]
         :values [[title message (:user/id (:identity request))]]}))


    (-> (response/redirect "/ask")
        (assoc :flash (str form-params)))))

(defn routes
  [system]
  ["" {:middleware (middleware/standard-authenticated-html-route-middleware system)}
   ["/ask" {:get (partial #'get-ask-handler system)
            :post (partial #'post-ask-handler system)}]])


(comment
  (let [db (user/db)]
    (jsonquery/execute!
      db
      {:select [:id
                [:discord_accounts {:select [:id :discord_user_id :discord_username]
                                    :from :discord.linked_account
                                    :join-on [:id :user_id]}]
                [:github_accounts {:select [:id :github_user_id :github_username]
                                   :from :github.linked_account
                                   :join-on [:id :user_id]}]
                [:questions {:select [:id :title]
                             :from :qa.question
                             :join-on [:id :asked_by_user_id]}]
                [:modules {:select [:name
                                    :version
                                    :target_platform
                                    :mandated
                                    :synthetic
                                    [:requires {:select [:module
                                                         :version
                                                         :static
                                                         :transitive
                                                         :mandated
                                                         :synthetic]
                                                :from :repository.module_requires
                                                :join-on [:id :module_id]}]
                                    [:exports {:select [:package
                                                        :to
                                                        :mandated
                                                        :synthetic]
                                               :from :repository.module_exports
                                               :join-on [:id :module_id]}]
                                    [:uses {:select [:service]
                                            :from :repository.module_uses
                                            :join-on [:id :module_id]}]
                                    [:provides {:select [:service
                                                         :with]
                                                :from :repository.module_provides
                                                :join-on [:id :module_id]}]
                                    [:packages {:select [:package]
                                                :from :repository.module_package
                                                :join-on [:id :module_id]}]
                                    [:hashes {:select [:module
                                                       :algorithm
                                                       :hash]
                                              :from :repository.module_hash
                                              :join-on [:id :module_id]}]]
                           :from :repository.module
                           :order-by [[:repository.module.name :asc]
                                      [:repository.module.version :desc]]
                           :join-on [:id :user_id]}]]
       :from :identity.user})))


