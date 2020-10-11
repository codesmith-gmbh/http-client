(ns codesmith.http.client.request
  (:require [codesmith.http.client.coercion :as c])
  (:import [java.net.http HttpRequest$BodyPublishers]))

(defn string-body
  ([string]
   (HttpRequest$BodyPublishers/ofString string))
  ([string encoding]
   (HttpRequest$BodyPublishers/ofString string (c/to-charset encoding))))
