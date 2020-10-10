(ns stan
  (:require [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream BufferedWriter OutputStreamWriter]
           [java.net.http HttpRequest HttpRequest$BodyPublishers]))

(comment
  (http/get "https://google.com")

  (let [baos (ByteArrayOutputStream.)
        writer (io/writer baos)]
    (json/generate-stream {:a 1} writer)
    (HttpRequest$BodyPublishers/ofByteArray (.toByteArray baos)))


  (json/generate-stream)
  )