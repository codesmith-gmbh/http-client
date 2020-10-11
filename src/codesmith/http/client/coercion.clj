(ns codesmith.http.client.coercion
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.nio.charset Charset]
           [clojure.lang Keyword]
           [java.net.http HttpClient$Redirect HttpClient$Version]))

(defprotocol ToFollowRedirects
  (to-follow-redirects [self]))

(extend-type HttpClient$Redirect
  ToFollowRedirects
  (to-follow-redirects [self] self))

(extend-type Keyword
  ToFollowRedirects
  (to-follow-redirects [self]
    (case self
      :redirect-always HttpClient$Redirect/ALWAYS
      :redirect-normal HttpClient$Redirect/NORMAL
      :redirect-never HttpClient$Redirect/NEVER
      (throw (IllegalArgumentException. (str "Unknown redirect: " self))))))

(extend-type String
  ToFollowRedirects
  (to-follow-redirects [self]
    (HttpClient$Redirect/valueOf self)))

(defprotocol ToVersion
  (to-version [self]))

(extend-type HttpClient$Version
  ToVersion
  (to-version [self] self))

(extend-type Keyword
  ToVersion
  (to-version [self]
    (case self
      :version-http-1.1 HttpClient$Version/HTTP_1_1
      :version-http-2 HttpClient$Version/HTTP_2
      (throw (IllegalArgumentException. (str "Unknown version: " self))))))

(defprotocol ToCharset
  (to-charset ^Charset [self]))

(extend-type Charset
  ToCharset
  (to-charset [self] self))

(extend-type String
  ToCharset
  (to-charset [self]
    (Charset/forName self)))

(extend-type Keyword
  ToCharset
  (to-charset [self]
    (Charset/forName (str/upper-case (name self)))))

(defprotocol ToUri
  (to-uri ^URI [self]))

(extend-type URI
  ToUri
  (to-uri [self] self))

(extend-type String
  ToUri
  (to-uri [self] (URI/create self)))
