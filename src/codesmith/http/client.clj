(ns codesmith.http.client
  (:require [codesmith.http.client.coercion :as c]
            [codesmith.http.client.interceptor.request :as ireq]
            [codesmith.http.client.interceptor.response :as ires]
            [codesmith.http.client.interceptor.others :as ioth]
            [codesmith.http.client.utils :as u])
  (:import [java.net.http HttpRequest HttpClient$Builder])
  (:refer-clojure :exclude [get]))

(set! *warn-on-reflection* true)

(defrecord HttpClient [^java.net.http.HttpClient http-client request-interceptors response-interceptors])

(def default-request-interceptors
  [ireq/uri-interceptor ireq/method-interceptor ireq/headers-interceptor ioth/measuring-interceptor])

(def default-response-interceptors
  [ires/status-interceptor ires/headers-full-conversion-interceptor ires/body-interceptor ioth/measuring-interceptor])

(defn http-client [& [{:keys [authenticator
                              connect-timeout
                              cookie-handler
                              executor
                              follow-redirects
                              priority
                              proxy
                              ssl-context
                              ssl-parameters
                              version
                              request-interceptors
                              response-interceptors]}]]
  (let [builder (java.net.http.HttpClient/newBuilder)
        builder (u/dot-builder-if builder :authenticator authenticator)
        builder (u/dot-builder-if builder :connectTimeout connect-timeout)
        builder (u/dot-builder-if builder :cookieHandler cookie-handler)
        builder (u/dot-builder-if builder :executor executor)
        builder (u/dot-builder-if builder :followRedirects c/to-follow-redirects follow-redirects)
        builder (u/dot-builder-if builder :priority priority)
        builder (u/dot-builder-if builder :proxy proxy)
        builder (u/dot-builder-if builder :sslContext ssl-context)
        builder (u/dot-builder-if builder :sslParameters ssl-parameters)
        builder (u/dot-builder-if builder :version c/to-version version)]
    (->HttpClient
      (.build ^HttpClient$Builder builder)
      (or request-interceptors default-request-interceptors)
      (or response-interceptors default-response-interceptors))))

(defn request [{:keys [^java.net.http.HttpClient http-client
                       request-interceptors
                       response-interceptors]} r]
  (let [request-map               (ireq/execute-enter request-interceptors r)
        ^HttpRequest http-request (::ireq/raw-request request-map)
        body-handler              (c/to-body-handler (or (:as request-map) :string))
        raw-response              (.send http-client http-request body-handler)]
    (if (::ireq/raw-response? request-map)
      raw-response
      (ires/execute-leave response-interceptors request-map raw-response))))


(defn get [^java.net.http.HttpClient client uri & [r]]
  (request client (merge {:method :get :uri uri} r)))

(comment
  (def client (http-client {:follow-redirects :redirect-normal
                            :version          :version-http-2}))


  (get client "https://www.google.com")
  (get-in [:headers "cache-control"])
  (def headers (:headers *1))
  (.firstValue headers "Cache-Control")
  (.get (.map headers) "status")
  (map identity (.map headers))

  (apply sorted-map-by String/CASE_INSENSITIVE_ORDER ["a" 1])
  )
