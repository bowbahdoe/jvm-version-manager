(ns dev.mccue.auth.duke
  (:import
    (dev.mccue.duke Duke Seed)
    (java.util Base64 Base64$Encoder UUID)
    (javax.imageio ImageIO)
    (org.apache.commons.io.output ByteArrayOutputStream)))

(defn uuid->duke
  [uuid]
  (Duke. (Seed. (UUID/.getLeastSignificantBits uuid))))

(defn duke->png-base64
  [duke]
  (let [image (Duke/.toBufferedImage_32x32 duke)
        baos (ByteArrayOutputStream.)]
    (ImageIO/write image "png" baos)
    (-> (Base64/getEncoder)
        (Base64$Encoder/.encodeToString (ByteArrayOutputStream/.toByteArray baos)))))
