(ns dev.mccue.sidebar.components
  (:require [dev.mccue.page.helpers :refer [classes]]))

(defn sidebar
  [request & main]
  (let [{:keys [user uri]} request]
    [:div {:class (classes ["flex"
                            "h-screen"])}
     [:aside {:class (classes ["w-60 border-r-4 bg-white flex flex-col"])}
      [:nav {:class (classes ["flex-col"
                              "flex"
                              "mx-2"
                              "h-full"])}
       [:a {:href  "/ask"
            :class (classes (cond->
                              ["flex items-center gap-1 px-4 py-2 my-2"
                               "justify-center"
                               "text-sm"
                               "font-semibold"
                               "text-black"
                               "shadow-xs"
                               "outline-2"
                               "hover:outline-2"
                               "hover:outline-offset-2"
                               "hover:outline-black"
                               "focus-visible:outline-2"
                               "focus-visible:outline-offset-2"
                               "focus-visible:outline-black"]
                              (= uri "/ask")
                              (conj "bg-yellow-200")))}
        "Ask"]
       [:a {:href  "/answer"
            :class (classes (cond->
                              ["flex items-center gap-1 px-4 py-2 my-2"
                               "justify-center"
                               "text-sm"
                               "font-semibold"
                               "text-black"
                               "shadow-xs"
                               "outline-2"
                               "hover:outline-2"
                               "hover:outline-offset-2"
                               "hover:outline-black"
                               "focus-visible:outline-2"
                               "focus-visible:outline-offset-2"
                               "focus-visible:outline-black"]
                              (= uri "/answer")
                              (conj "bg-yellow-200")))}
        "Answer"]
       [:a {:href  "/search"
            :class (classes (cond->
                              ["flex items-center gap-1 px-4 py-2 my-2"
                               "justify-center"
                               "text-sm"
                               "font-semibold"
                               "text-black"
                               "shadow-xs"
                               "outline-2"
                               "hover:outline-2"
                               "hover:outline-offset-2"
                               "hover:outline-black"
                               "focus-visible:outline-2"
                               "focus-visible:outline-offset-2"
                               "focus-visible:outline-black"]
                              (= uri "/search")
                              (conj "bg-yellow-200")))}
        "Search"]
       [:a {:href  "/chat"
            :class (classes (cond->
                              ["flex items-center gap-1 px-4 py-2 my-2"
                               "justify-center"
                               "text-sm"
                               "font-semibold"
                               "text-black"
                               "shadow-xs"
                               "outline-2"
                               "hover:outline-2"
                               "hover:outline-offset-2"
                               "hover:outline-black"
                               "focus-visible:outline-2"
                               "focus-visible:outline-offset-2"
                               "focus-visible:outline-black"]
                              (= uri "/chat")
                              (conj "bg-yellow-200")))}
        "Chat"]

       [:div {:class "grow"}]

       (when user
         (println uri)
         (list
           [:a {:href  "/profile"
                :class (classes (cond->
                                  ["flex items-center gap-1 px-4 py-2 my-2"
                                   "justify-center"
                                   "text-sm"
                                   "font-semibold"
                                   "text-black"
                                   "shadow-xs"
                                   "outline-2"
                                   "hover:outline-2"
                                   "hover:outline-offset-2"
                                   "hover:outline-black"
                                   "focus-visible:outline-2"
                                   "focus-visible:outline-offset-2"
                                   "focus-visible:outline-black"]
                                  (= uri "/profile")
                                  (conj "bg-yellow-200")))}
            "Profile"]

           [:a {:href  "/logout"
                :class (classes
                         ["flex"
                          "items-center"
                          "gap-3"
                          "px-4"
                          "py-2"
                          "my-2"
                          "justify-center"
                          "text-sm"
                          "font-semibold"
                          "text-white"
                          "bg-black"
                          "shadow-xs"
                          "outline-2"
                          "hover:outline-2"
                          "hover:outline-offset-2"
                          "hover:outline-black"
                          "focus-visible:outline-2"
                          "focus-visible:outline-offset-2"
                          "focus-visible:outline-black"])}
            "Logout"]))

       (when-not user
         [:a {:href  "/not-oauth/atproto"
              :class (classes
                       ["flex"
                        "items-center"
                        "gap-3"
                        "px-4"
                        "py-2"
                        "my-2"
                        "justify-center"
                        "text-sm"
                        "font-semibold"
                        "text-white"
                        "bg-black"
                        "shadow-xs"
                        "outline-2"
                        "hover:outline-2"
                        "hover:outline-offset-2"
                        "hover:outline-black"
                        "focus-visible:outline-2"
                        "focus-visible:outline-offset-2"
                        "focus-visible:outline-black"])}
          "Login"])]]
     [:main {:class (classes ["flex-1 overflow-y-auto"])}
      (apply list main)]]))