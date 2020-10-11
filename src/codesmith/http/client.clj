(ns codesmith.http.client
  (:require [codesmith.http.client.coercion :as c]
            [codesmith.http.client.interceptor.request :as ireq]
            [codesmith.http.client.interceptor.response :as ires]
            [codesmith.http.client.interceptor.dual :as dual]
            [codesmith.http.client.utils :as u])
  (:import [java.net.http HttpRequest HttpClient$Builder HttpResponse$BodyHandlers])
  (:refer-clojure :exclude [get send]))

(defrecord HttpClient [^java.net.http.HttpClient http-client request-interceptors response-interceptors])

(def default-request-interceptors
  [ireq/uri-interceptor ireq/method-interceptor ireq/headers-interceptor dual/measuring-interceptor])

(def default-response-interceptors
  [ires/status-interceptor ires/headers-full-conversion-interceptor ires/body-interceptor dual/measuring-interceptor])

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

(defn send [{:keys [^java.net.http.HttpClient http-client
                       request-interceptors
                       response-interceptors]} r]
  (let [request-map               (ireq/execute-enter request-interceptors r)
        ^HttpRequest http-request (::ireq/raw-request request-map)
        body-handler              (or (:body-handler request-map) (HttpResponse$BodyHandlers/ofString))
        raw-response              (.send http-client http-request body-handler)]
    (if (::ireq/raw-response? request-map)
      raw-response
      (ires/execute-leave response-interceptors request-map raw-response))))


(defn get [client uri & [r]]
  (send client (merge {:method :get :uri uri} r)))

(defn head [client uri & [r]]
  (send client (merge {:method :head :uri uri} r)))

(defn post [client uri & [r]]
  (send client (merge {:method :post :uri uri} r)))

(defn put [client uri & [r]]
  (send client (merge {:method :put :uri uri} r)))

(defn delete [client uri & [r]]
  (send client (merge {:method :delete :uri uri} r)))
