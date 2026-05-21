(ns dev.mccue.auth.workers
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc]
            [dev.mccue.auth.duke :as duke])
  (:import (java.util UUID)))


(defn identity_user-genProfileImage
  "When a user signs up, we generate a profile photo for them if they didn't get one
   from some other mechanism."
  [{:system/keys [db]} _job-type payload]
  (let [{:keys [id profile_image_png_base64]} payload
        id (UUID/fromString id)]
    (when-not profile_image_png_base64
      (jdbc/execute! db (sql/format
                          {:update :identity.user
                           :set {:profile_image_png_base64 (duke/duke->png-base64 (duke/uuid->duke id))}
                           :where [:and
                                   [:= :profile_image_png_base64 nil]
                                   [:= :id id]]}
                          {:quoted true})))))

(defn workers
  []
  {:identity.user/genProfileImage #'identity_user-genProfileImage})