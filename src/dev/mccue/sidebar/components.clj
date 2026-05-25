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
       [:p {:class (classes ["text-md" "font-bold" "text-center" "outline-2" "border-4" "mb-3" "mt-3"])}
        "JVM Community Site"]
       [:p {:class (classes ["text-lg" "font-bold" "text-center" "mt-4"])}
        "Community"]
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

       [:p {:class (classes ["text-lg" "font-bold" "text-center" "mt-4"])}
        "Libraries"]

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

       [:a {:href  "/consume"
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
                              (= uri "/consume")
                              (conj "bg-yellow-200")))}
        "Consume"]

       [:a {:href  "/publish"
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
                              (= uri "/publish")
                              (conj "bg-yellow-200")))}
        "Publish"]

       [:p {:class (classes ["text-lg" "font-bold" "text-center" "mt-4"])}
        "Career"]

       [:a {:href  "/jobs"
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
                              (= uri "/jobs")
                              (conj "bg-yellow-200")))}
        "Jobs"]

       [:a {:href  "/interview_prep"
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
                              (= uri "/interview_prep")
                              (conj "bg-yellow-200")))}
        "Interviews"]

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