(ns dev.mccue.atproto.diddy
  (:require [cheshire.core :as cheshire]
            [clojure.string :as string])
  (:import (java.util Hashtable)
           (javax.naming NameNotFoundException NamingEnumeration)
           (javax.naming.directory Attribute Attributes DirContext InitialDirContext)))



(defn get-did-dns
  [atproto-handle]
  (try
    (let [dir (InitialDirContext.
                (doto (Hashtable.)
                  (.put "java.naming.factory.initial",
                        "com.sun.jndi.dns.DnsContextFactory")))
          attrs (^[String String/1]
                  DirContext/.getAttributes
                  dir
                  (str "_atproto." atproto-handle)
                  (into-array String ["TXT"]))]

      (when-let [txt (Attributes/.get attrs "TXT")]
        (let [e (Attribute/.getAll txt)]
          (loop []
            (when (NamingEnumeration/.hasMore e)
              (let [value (NamingEnumeration/.next e)]
                (if (string/starts-with? value "did=")
                  (string/replace-first value "did=" "")
                  (recur))))))))
    (catch NameNotFoundException _
      nil)))

(defn get-did-https
  [atproto-handle]
  (try (slurp (str "https://" atproto-handle "/.well-known/atproto-did"))
       (catch Exception _ nil)))

(defn get-did
  [atproto-handle]
  (or (get-did-dns atproto-handle)
      (get-did-https atproto-handle)))

(comment
  (resolve-did-document (get-did "mccue.dev")))

(defn resolve-did-document
  [did]
  ;; TODO: did:web
  (try
    (cheshire/parse-string-strict
      (slurp (str "https://plc.directory/" did)))
    (catch Exception _ nil)))

(defn resolve-handle
  [did-document]
  (->> (get did-document "alsoKnownAs")
       (filter #(string/starts-with? % "at://"))
       (map #(string/replace-first % "at://" ""))
       (first)))

(defn resolve-service-endpoint
  [did-document]
  (-> (->> (get did-document "service")
           (filter #(= (% "id") "#atproto_pds"))
           (first))
      (get "serviceEndpoint")))

(defn resolve-service-info
  [service-endpoint]
  (try
    (cheshire/parse-string-strict
      (slurp (str service-endpoint "/.well-known/oauth-protected-resource")))
    (catch Exception _ nil)))

(defn resolve-authorization-server-endpoint
  [service-info]
  (get-in service-info ["authorization_servers" 0]))

(defn resolve-authorization-server-description
  [authorization-server-endpoint]
  (cheshire/parse-string-strict
    (slurp (str authorization-server-endpoint
                "/.well-known/oauth-authorization-server"))))