(ns dev.mccue.register.workers
  (:require [honey.sql :as sql]
            [next.jdbc :as jdbc])
  (:import (dev.mccue.duke Duke Seed)
           (java.util Base64 Base64$Encoder UUID)
           (javax.imageio ImageIO)
           (org.apache.commons.io.output ByteArrayOutputStream)))

(defn uuid->duke
  [uuid]
  (Duke. (Seed. (UUID/.getLeastSignificantBits uuid))))

(defn duke->png-base64
  [duke]
  (let [image (Duke/.toBufferedImage_32x32 duke)
        baos  (ByteArrayOutputStream.)]
    (ImageIO/write image "png" baos)
    (-> (Base64/getEncoder)
        (Base64$Encoder/.encodeToString (ByteArrayOutputStream/.toByteArray baos)))))

(defn identity_user-genProfileImage
  "When a user signs up, we generate a profile photo for them."
  [{:system/keys [db]} _job-type payload]
  (let [{:keys [id profile_image_png_base64]} payload
        id (UUID/fromString id)]
    (when-not profile_image_png_base64
      (jdbc/execute! db (sql/format
                          {:update :identity.user
                           :set {:profile_image_png_base64 (duke->png-base64 (uuid->duke id))}
                           :where [:and
                                   [:= :profile_image_png_base64 nil]
                                   [:= :id id]]}
                          {:quoted true})))))

(defn workers
  []
  {:identity.user/genProfileImage #'identity_user-genProfileImage})