(ns stan
  (:require [codesmith.http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(comment
  (def client (http/http-client {:follow-redirects :redirect-normal
                                 :version          :version-http-2}))

  (get-in (http/head client "https://www.google.com") [:headers "cache-control"] )
  (get-in [:headers "cache-control"])
  (def headers (:headers *1))
  (.firstValue headers "Cache-Control")
  (.get (.map headers) "status")
  (map identity (.map headers))

  (apply sorted-map-by String/CASE_INSENSITIVE_ORDER ["a" 1])
  )