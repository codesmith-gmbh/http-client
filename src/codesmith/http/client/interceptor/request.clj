(ns codesmith.http.client.interceptor.request
  (:require [codesmith.http.client.coercion :as c]
            [codesmith.http.client.body :as body]
            [codesmith.http.client.utils :refer [some-assoc->]]
            [codesmith.http.client.interceptor.protocols :as proto])
  (:import [java.net.http HttpRequest$Builder HttpRequest HttpRequest$BodyPublishers]))


(defn- execute-leave! [interceptors ^HttpRequest$Builder builder request-map]
  (let [interceptor-count (long (count interceptors))]
    (loop [x (long 0)]
      (when (< x interceptor-count)
        (let [interceptor (nth interceptors x)]
          (proto/leave! interceptor builder request-map)
          (recur (inc x)))))
    (assoc request-map ::raw-request (.build builder))))

(defn execute-enter [interceptors request-map]
  (loop [x           (long (dec (count interceptors)))
         request-map request-map]
    (if (>= x 0)
      (let [interceptor (nth interceptors x)]
        (recur (dec x) (proto/enter interceptor request-map)))
      (execute-leave! interceptors (HttpRequest/newBuilder) request-map))))

(deftype FunctionInterceptor [enter leave!]
  proto/RequestInterceptor
  (enter [self request-map]
    (if-let [enter (.-enter self)]
      (enter request-map)
      request-map))
  (leave! [self builder request-map]
    (when-let [leave! (.-leave_BANG_ self)]
      (leave! builder request-map))))

(defn set-header [^HttpRequest$Builder builder ^String header-name ^String value]
  (.setHeader builder header-name value))

(defn set-headers [builder headers]
  (doseq [[header-name values] headers]
    (if (coll? values)
      (doseq [value values]
        (set-header builder header-name value))
      (set-header builder header-name values))))

(deftype HeadersInterceptor []
  proto/RequestInterceptor
  (enter [_ {:keys [accept content-type headers] :as request-map}]
    (assoc request-map :headers (some-assoc-> headers
                                              "Accept" accept
                                              "Content-Type" content-type)))
  (leave! [_ builder {:keys [headers]}]
    (set-headers builder headers)))

(deftype UriInterceptor []
  proto/RequestInterceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [uri]}]
    (.uri ^HttpRequest$Builder builder (c/to-uri uri))))

(defn body-publisher [{:keys [body-publisher]}]
  (or body-publisher (HttpRequest$BodyPublishers/noBody)))

(deftype MethodInterceptor []
  proto/RequestInterceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [method body]}]
    (case method
      ("GET" :get) (.GET ^HttpRequest$Builder builder)
      ("PUT" :put) (.PUT ^HttpRequest$Builder builder (body-publisher body))
      ("POST" :post) (.POST ^HttpRequest$Builder builder (body-publisher body))
      ("DELETE" :delete) (.DELETE ^HttpRequest$Builder builder)
      (.method ^HttpRequest$Builder builder (.toUpperCase (name method)) (body-publisher body)))))

(deftype JsonInterceptor []
  proto/RequestInterceptor
  (enter [_ request-map]
    (assoc request-map :send-as body/send-as-json
                       :accept-as body/accept-as-json))
  (leave! [_ _ _]))

(deftype BodyInterceptor []
  proto/RequestInterceptor
  (enter [_ {:keys [send-as accept-as] :as request-map}]
    (cond-> request-map
            accept-as (assoc :accept (:content-type accept-as)
                             :body-handler (:body-handler accept-as)
                             :body-transform (:body-transform accept-as))
            send-as (assoc :content-type (:content-type send-as)
                           :body-publisher ((:body-publisher-for send-as) request-map))))
  (leave! [_ _ _]))

(deftype AuthenticationHeaderInterceptor [^String authentication-header]
  proto/RequestInterceptor
  (enter [_ request-map]
    (assoc-in request-map [:headers "Authentication"] authentication-header))
  (leave! [_ _ _]))

(def uri-interceptor
  (->UriInterceptor))

(def body-interceptor
  (->BodyInterceptor))

(def headers-interceptor
  (->HeadersInterceptor))

(def method-interceptor
  (->MethodInterceptor))

(def json-interceptor
  (->JsonInterceptor))

