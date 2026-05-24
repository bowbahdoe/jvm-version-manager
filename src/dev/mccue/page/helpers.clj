(ns dev.mccue.page.helpers
  (:require [clojure.string :as string]
            [hiccup2.core :as hiccup]))


(defn hiccup-response
  [& {:keys [body status]}]
  {:status  (or status 200)
   :headers {"Content-Type" "text/html"}
   :body    (str (hiccup/html body))})

(def solid-box
  ["margin: 4px"
   "border: 2px solid black"
   "border-radius: 2px"
   "width: fit-content"
   "height: fit-content"
   "padding: 4px"])

(def dashed-box
  ["margin: 4px"
   "border: 2px dashed black"
   "border-radius: 2px"
   "width: fit-content"
   "height: fit-content"
   "padding: 4px"])

(defmacro css
  [styles]
  (if (and (vector? styles)
           (every? string? styles))
    (string/join ";" styles)
    `(string/join ";" ~styles)))

(defmacro classes
  [classes]
  (if (and (vector? classes)
           (every? string? classes))
    (string/join " " classes)
    `(string/join " " ~classes)))

(defn page-response
  [& {:keys [title head body status]}]
  (hiccup-response
    :status status
    :body [:html {:lang  "en"
                  :class (classes ["h-full"])}
           [:head
            (list
              [:script {:src "/htmx.js"}]
              [:script {:src   "/alpine.js"
                        :defer true}]
              [:link {:href "/tailwind.css" :rel "stylesheet"}]
              [:title title]
              head)]
           [:body {:class (classes ["h-full"])
                   :style "font-family: monospace"} body]]))

(defn _404-page-response
  [& _]
  (page-response
    :title "Page Not Found"
    :body [:main
           {:class
            (classes ["grid"
                      "min-h-full"
                      "place-items-center"
                      "px-6"
                      "py-24"
                      "sm:py-32"
                      "lg:px-8"])}
           [:div
            {:class "text-center"}
            [:h1
             {:class
              (classes ["mt-4"
                        "text-5xl"
                        "font-semibold"
                        "tracking-tight"
                        "text-balance"
                        "sm:text-7xl"])}
             "404"]
            [:div
             {:class "mt-10 flex items-center justify-center gap-x-6"}
             [:a
              {:href "/",
               :class
               (classes ["rounded-md"
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
                         "focus-visible:outline-black"])}
              "Go back home"]]]]))