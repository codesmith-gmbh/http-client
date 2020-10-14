(ns stan
  (:require [codesmith.http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [codesmith.http.client.interceptor.response :as ires]
            [codesmith.http.client.interceptor.dual :as dual]
            [codesmith.http.client.interceptor.request :as ireq]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)
(comment
  (def client (http/http-client {:follow-redirects     :redirect-normal
                                 :version              :version-http-2
                                 :request-interceptors [ireq/uri-interceptor ireq/method-interceptor ireq/headers-interceptor ireq/body-interceptor ireq/json-interceptor dual/measuring-interceptor]}))

  (http/get client "http://localhost:9000/greet")

  (get-in (http/head client "https://www.google.com") [:headers "cache-control"])
  (get-in [:headers "cache-control"])
  (def headers (:headers *1))
  (.firstValue headers "Cache-Control")
  (.get (.map headers) "status")
  (map identity (.map headers))

  (apply sorted-map-by String/CASE_INSENSITIVE_ORDER ["a" 1])

  )