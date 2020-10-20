(ns stan
  (:require [codesmith.http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [codesmith.logger :as log]
            [codesmith.http.client.interceptor.response :as ires]
            [codesmith.http.client.interceptor.dual :as dual]
            [codesmith.http.client.interceptor.request :as ireq]
            [codesmith.http.client.interceptor.protocols :as proto]))

(log/deflogger)

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def token (atom nil))
(def trials (atom 0))

(defn refresh-token! [http-client]
  (let [token-value (get-in
                      (http/get http-client "http://localhost:9000/token")
                      [:body :token])]
    (println "refreshing!!!")
    (reset! token token-value)))

(deftype RefreshingTokenAuth [refresh-http-client]
  proto/RequestInterceptor
  (enter [_ request-map]
    (when-not @token
      (refresh-token! refresh-http-client))
    (assoc-in request-map [:headers "authentication"] @token))
  (leave! [_ _ _])

  proto/ResponseInterceptor
  (leave [_ request-map response {:keys [status] :as response-map}]
    (if (and (= status 403))
      (do
        (refresh-token! refresh-http-client)
        (assoc response-map ::retry true))
      response-map)))


(defn retry-get [http-client uri & [request-map]]
  (let [response-map (http/get http-client uri request-map)]
    (if (::retry response-map)
      (do (println "RETRYING!!!")
          (http/get http-client uri request-map))
      response-map)))

(comment
  (def refreshing-client (http/http-client {:follow-redirects      :redirect-normal
                                            :version               :version-http-2
                                            :request-interceptors  [ireq/uri-interceptor ireq/method-interceptor ireq/headers-interceptor ireq/body-interceptor ireq/edn-interceptor dual/measuring-interceptor]
                                            :response-interceptors [ires/status-interceptor ires/headers-full-conversion-interceptor ires/body-interceptor dual/measuring-interceptor]}))

  (def refreshing-token-interceptor (->RefreshingTokenAuth refreshing-client))

  (def client (http/http-client {:follow-redirects      :redirect-normal
                                 :version               :version-http-2
                                 :request-interceptors  [ireq/uri-interceptor ireq/method-interceptor ireq/headers-interceptor ireq/body-interceptor ireq/edn-interceptor refreshing-token-interceptor dual/measuring-interceptor]
                                 :response-interceptors [ires/status-interceptor ires/headers-full-conversion-interceptor ires/body-interceptor refreshing-token-interceptor dual/measuring-interceptor]}))

  (retry-get client "http://localhost:9000/greet")
  token

  (get-in (http/head client "https://www.google.com") [:headers "cache-control"])
  (get-in [:headers "cache-control"])
  (def headers (:headers *1))
  (.firstValue headers "Cache-Control")
  (.get (.map headers) "status")
  (map identity (.map headers))

  (apply sorted-map-by String/CASE_INSENSITIVE_ORDER ["a" 1])

  (log/debug-c {})

  )