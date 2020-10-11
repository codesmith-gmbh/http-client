(ns codesmith.http.client.coercion
  (:require [clojure.string :as str])
  (:import [java.net URI]
           [java.nio.charset Charset]
           [clojure.lang Keyword]
           [java.net.http HttpRequest$BodyPublisher HttpRequest$BodyPublishers HttpResponse$BodyHandler HttpResponse$BodyHandlers HttpClient$Redirect HttpClient$Version]
           [java.io File]))

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

(defmulti value-to-mime identity)

(defmacro defmime [keyword value]
  `(defmethod value-to-mime ~keyword [~'_] ~value))

(defmime :json "application/json")

(defn to-mime [mime]
  (and mime
       (if (string? mime)
         mime
         (value-to-mime mime))))

(defprotocol ToUri
  (to-uri ^URI [self]))

(extend-type URI
  ToUri
  (to-uri [self] self))

(extend-type String
  ToUri
  (to-uri [self] (URI/create self)))







(defprotocol ToMethod
  (to-method ^String [self]))

(extend-type String
  ToMethod
  (to-method [self] self))

(extend-type Keyword
  ToMethod
  (to-method [self]
    (str/upper-case (name self))))

(defprotocol ToBodyPublisher
  (to-body-publisher ^HttpRequest$BodyPublisher [self]))

(extend-type String
  ToBodyPublisher
  (to-body-publisher [self]
    (HttpRequest$BodyPublishers/ofString self)))

(extend-type File
  ToBodyPublisher
  (to-body-publisher [self]
    (HttpRequest$BodyPublishers/ofFile (.toPath self))))

(extend-type HttpRequest$BodyPublisher
  ToBodyPublisher
  (to-body-publisher [self]
    self))

(extend-type nil
  ToBodyPublisher
  (to-body-publisher [self]
    (HttpRequest$BodyPublishers/noBody)))

(defprotocol ToBodyHandler
  (to-body-handler [self]))

(extend-type HttpResponse$BodyHandler
  ToBodyHandler
  (to-body-handler [self] self))

(extend-type Keyword
  ToBodyHandler
  (to-body-handler [self]
    (case self
      :string (HttpResponse$BodyHandlers/ofString)
      :stream (HttpResponse$BodyHandlers/ofInputStream))))