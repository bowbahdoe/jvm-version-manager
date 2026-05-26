(ns dev.mccue.index.routes
  (:require [dev.mccue.jsonquery :as jsonquery]
            [dev.mccue.middleware :as middleware]
            [dev.mccue.page.helpers :as page-helpers :refer [classes]]
            [dev.mccue.sidebar.components :as sidebar-components]))


(defn index-handler
  [{:system/keys [db]} request]
  (page-helpers/page-response
    :title "ALL USERS"
    :body (sidebar-components/sidebar
            request
            [:div {:class (classes ["flex" "flex-col"])}
             [:p "Atmosphere\n\nConnect with your Atmosphere account"]
             [:p "Handle"]
             [:input {:type "text"}]
             [:p "What is an Atmosphere account?"]
             [:p "Don't Care:"]
             [:p "Login with "
              [:span [:img {:src "/bluesky.svg"
                            :class (classes ["size-[1em]"])}]]
              "Bluesky"]

             [:p "jvm.mccue.dev uses the "
                  [:span [:a {:href "https://atproto.com/"
                              :class (classes ["hover:underline" "italic"])} "AT Protocol"]]
                 " to power many of its social features, allowing users to own their data
and use one account for all compatible applications.
Once you create an account, you can use other apps like Bluesky
and Tangled with the same account."]
             [:p "Connect"]
             [:p "Create a new account"]]
             ; https://simpleicons.org/?q=bsk&modal=icon

            #_(when-not (:user request)
                [:a {:href  "/oauth2/atproto/launch/mccue.dev"
                     :class (classes ["rounded-md"
                                      "bg-black"
                                      "px-3.5"
                                      "py-2.5"
                                      "text-sm"
                                      "font-semibold"
                                      "text-white"
                                      "shadow-xs"
                                      "hover:outline-2"
                                      "hover:outline-offset-2"
                                      "hover:outline-black"
                                      "focus-visible:outline-2"
                                      "focus-visible:outline-offset-2"
                                      "focus-visible:outline-black"])} "@Login (OAuth)"]))))


(defn routes
  [system]
  ["" {:middleware (middleware/standard-html-route-middleware system)}
   [["/" {:get (partial #'index-handler system)}]]])
