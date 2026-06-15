(ns dev.mccue.atproto.cid
  (:require [clojure.java.io :as io])
  (:import (java.io ByteArrayOutputStream InputStream OutputStream)
           (java.security MessageDigest)
           (java.util HexFormat)
           (org.apache.commons.codec.binary Base32)))

; bafkreia7vvxgxz2vo6a6juzxfhkjvyonzd65u37eo65qzrum4ni6v7p3vm
; 1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab

(defn- string-encoded->byte-string
  [s]
  (when (String/.isEmpty s)
    (throw (IllegalArgumentException. "String encoded CID must not be empty")))
  (when (not= (String/.charAt s 0) \b)
    (throw (IllegalArgumentException. "String encoded CID must start with 'b'")))
  (-> (Base32.)
      (Base32/.decode (String/.substring s 1))))

(defn from-bytes
  [bytes]
  (let [is (io/input-stream bytes)
        version (InputStream/.read is)]
    (when (< version 0)
      (throw (IllegalArgumentException. "Reached end of bytes just before version byte.")))
    (when (not= version 1)
      (throw (IllegalArgumentException. "Version must be 1.")))
    (let [codec (InputStream/.read is)]
      (when (< version 0)
        (throw (IllegalArgumentException. "Reached end of bytes just before codec byte.")))
      (when (not (#{0x55 0x71} codec))
        (throw (IllegalArgumentException. (str "Codec must be one of 0x55 or 0x71. Got " codec))))
      (let [hash-type (InputStream/.read is)]
        (when (< hash-type 0)
          (throw (IllegalArgumentException. "Reached end of bytes just before hash-type byte.")))
        (when (not (= hash-type 0x12))
          (throw (IllegalArgumentException. (str "Hash type must be 0x12. Got " hash-type))))
        (let [hash-size (InputStream/.read is)]
          (when (< hash-size 0)
            (throw (IllegalArgumentException. "Reached end of bytes just before hash-size byte.")))
          (when (not= hash-size 32)
            (throw (IllegalArgumentException. "Hash size must be 32. Got " hash-size)))
          (let [out (ByteArrayOutputStream.)
                written (InputStream/.transferTo is out)]
            (when (not= written 32)
              (throw (IllegalArgumentException. (str "Expected exactly 32 remaining bytes. Got " written))))
            {:version   version
             :codec     (if (= codec 0x55)
                          :raw
                          :drisl)
             :hash-type :sha256
             :hash-size 32
             :digest    (ByteArrayOutputStream/.toByteArray out)}))))))


(defn from-string
  [s]
  (-> s
      (string-encoded->byte-string)
      (from-bytes)))

(defn cid-string->sha256-hex-string
  [cid]
  (let [{:keys [digest]} (from-string cid)]
    ;; Assumes only sha256
    (HexFormat/.formatHex (HexFormat/of) digest)))

(defn sha256-bytes->cid-bytes
  [sha256-bytes]
  (let [out (ByteArrayOutputStream.)]
    (OutputStream/.write out 1)    ;; version
    (OutputStream/.write out 0x55) ;; raw
    (OutputStream/.write out 0x12) ;; sha256
    (OutputStream/.write out 32)   ;; Length
    (^[byte/1] OutputStream/.write out sha256-bytes)
    (ByteArrayOutputStream/.toByteArray out)))

(defn- bytes->base32
  [bytes]
  (-> (Base32/.encodeAsString (Base32. 0)
                              bytes)
      (String/.toLowerCase)
      (String/.replace "=" "")))

(defn sha256-bytes->cid-string
  [sha256-bytes]
  (let [cid-bytes (sha256-bytes->cid-bytes sha256-bytes)]
    (str "b" (bytes->base32 cid-bytes))))

(defn sha256-hex-string->cid-bytes
  [sha256]
  (sha256-bytes->cid-bytes (HexFormat/.parseHex (HexFormat/of) sha256)))

(defn sha256-hex-string->cid-string
  [sha256]
  (str "b" (bytes->base32 (sha256-hex-string->cid-bytes sha256))))

(defn bytes->sha256-bytes
  [bytes]
  (MessageDigest/.digest
    (MessageDigest/getInstance "sha256")
    bytes))


(defn bytes->cid-string
  [bytes]
  (-> bytes
      (bytes->sha256-bytes)
      (sha256-bytes->cid-string)))



(comment
  (from-string "bafkreia7vvxgxz2vo6a6juzxfhkjvyonzd65u37eo65qzrum4ni6v7p3vm")
  (cid-string->sha256-hex-string "bafkreia7vvxgxz2vo6a6juzxfhkjvyonzd65u37eo65qzrum4ni6v7p3vm")
  (sha256-hex-string->cid-string (cid-string->sha256-hex-string "bafkreia7vvxgxz2vo6a6juzxfhkjvyonzd65u37eo65qzrum4ni6v7p3vm")))
