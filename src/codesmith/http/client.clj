(ns codesmith.http.client
  (:require [clojure.string :as str]
            [clojure.core.reducers :as r]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.net.http HttpClient$Version HttpRequest HttpResponse$BodyHandlers HttpClient$Redirect HttpResponse HttpRequest$BodyPublisher HttpRequest$BodyPublishers HttpRequest$Builder HttpResponse$BodyHandler HttpHeaders]
           [java.net URI]
           [clojure.lang Keyword]
           [java.io File]
           [java.nio.charset Charset]
           [java.util Map$Entry List])
  (:refer-clojure :exclude [get]))

(set! *warn-on-reflection* true)

(defprotocol ToFollowRedirects
  (to-follow-redirects [self]))

(extend-type HttpClient$Redirect
  ToFollowRedirects
  (to-follow-redirects [self] self))

(extend-type Keyword
  ToFollowRedirects
  (to-follow-redirects [self]
    (case self
      ::redirect-always HttpClient$Redirect/ALWAYS
      ::redirect-normal HttpClient$Redirect/NORMAL
      ::redirect-never HttpClient$Redirect/NEVER
      (throw (IllegalArgumentException. (str "Unknown redirect: " self))))))

(extend-type String
  ToFollowRedirects
  (to-follow-redirects [self]
    (HttpClient$Redirect/valueOf self)))

(defprotocol ToVersion
  (to-version ^HttpClient$Version [self]))

(extend-type HttpClient$Version
  ToVersion
  (to-version [self] self))

(extend-type Keyword
  ToVersion
  (to-version [self]
    (case self
      ::version-http-1.1 HttpClient$Version/HTTP_1_1
      ::version-http-2 HttpClient$Version/HTTP_2
      (throw (IllegalArgumentException. (str "Unknown version: " self))))))

(defmacro dot-builder-if
  ([builder method value]
   `(if-let [value# ~value]
      (. ~builder ~(symbol method) value#)
      ~builder))
  ([builder method to-fn value]
   `(if-let [value# (~to-fn ~value)]
      (. ~builder ~(symbol method) value#)
      ~builder)))



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

(defn string-body
  ([string]
   (to-body-publisher string))
  ([string encoding]
   (HttpRequest$BodyPublishers/ofString string (to-charset encoding))))

(defn set-header [^HttpRequest$Builder builder ^String header-name ^String value]
  (.setHeader builder header-name value))

(defn set-headers [builder headers]
  (doseq [[header-name values] headers]
    (if (coll? values)
      (doseq [value values]
        (set-header builder header-name value))
      (set-header builder header-name values))))

(defn assoc-if
  ([m k v]
   (if v (assoc m k v) m))
  ([m k1 v1 k2 v2]
   (assoc-if (assoc-if m k1 v1) k2 v2)))

(defprotocol RequestInterceptor
  (enter [self request-map])
  (leave! [self ^HttpRequest$Builder builder request-map]))

(defprotocol ResponseInterceptor
  (leave [self ^HttpResponse response response-map]))

(deftype FunctionInterceptor [enter leave!]
  RequestInterceptor
  (enter [self request-map]
    (if-let [enter (.-enter self)]
      (enter request-map)
      request-map))
  (leave! [self builder request-map]
    (when-let [leave! (.-leave_BANG_ self)]
      (leave! builder request-map))))

(deftype HeadersInterceptor []
  RequestInterceptor
  (enter [_ {:keys [accept content-type headers] :as request-map}]
    (assoc request-map :headers (assoc-if headers
                                          "Accept" (to-mime accept)
                                          "Content-Type" (to-mime content-type))))
  (leave! [_ builder {:keys [headers]}]
    (set-headers builder headers)))

(deftype UriInterceptor []
  RequestInterceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [uri]}]
    (.uri ^HttpRequest$Builder builder (to-uri uri))))

(deftype MethorInterceptor []
  RequestInterceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [method body]}]
    (case method
      :get (.GET ^HttpRequest$Builder builder)
      :put (.PUT ^HttpRequest$Builder builder (to-body-publisher body))
      :post (.POST ^HttpRequest$Builder builder (to-body-publisher body))
      :delete (.DELETE ^HttpRequest$Builder builder)
      (.method ^HttpRequest$Builder builder (to-method method) (to-body-publisher body)))))

(deftype JsonInterceptor []
  RequestInterceptor
  (enter [_ request-map]
    (assoc request-map :as :json
                       :accept :json
                       :content-type :json))
  (leave! [_ _ _]))

(def uri-interceptor
  (->UriInterceptor))

(def headers-interceptor
  (->HeadersInterceptor))

(def method-interceptor
  (->MethorInterceptor))

(def json-interceptor
  (->JsonInterceptor))

(defn- execute-leave [interceptors ^HttpRequest$Builder builder request-map]
  (let [interceptor-count (long (count interceptors))]
    (loop [x (long 0)]
      (when (< x interceptor-count)
        (let [interceptor (nth interceptors x)]
          (leave! interceptor builder request-map)
          (recur (inc x)))))
    (assoc request-map ::request (.build builder))))

(defn execute [interceptors request-map]
  (loop [x           (long (dec (count interceptors)))
         request-map request-map]
    (if (>= x 0)
      (let [interceptor (nth interceptors x)]
        (recur (dec x) (enter interceptor request-map)))
      (execute-leave interceptors (HttpRequest/newBuilder) request-map))))

(defn convert-headers [^HttpHeaders headers]
  (apply sorted-map-by
         String/CASE_INSENSITIVE_ORDER
         (mapcat (fn [^Map$Entry entry]
                   (let [key (.getKey entry)]
                     (if (not= key ":status")
                       [key (let [^List value (.getValue entry)]
                              (if (= (.size value) 1)
                                (.get value 0)
                                (vec value)))])))
                 (.map headers))))

(defn convert-raw-response [^HttpResponse raw-response]
  {:status  (.statusCode raw-response)
   :body    (.body raw-response)
   :headers (convert-headers (.headers raw-response))})



(defrecord HttpClient [^java.net.http.HttpClient http-client request-interceptors response-interceptors])

(def default-request-interceptors
  [uri-interceptor method-interceptor headers-interceptor])

(def default-response-interceptors
  [])

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
        builder (dot-builder-if builder :authenticator authenticator)
        builder (dot-builder-if builder :connectTimeout connect-timeout)
        builder (dot-builder-if builder :cookieHandler cookie-handler)
        builder (dot-builder-if builder :executor executor)
        builder (dot-builder-if builder :followRedirects to-follow-redirects follow-redirects)
        builder (dot-builder-if builder :priority priority)
        builder (dot-builder-if builder :proxy proxy)
        builder (dot-builder-if builder :sslContext ssl-context)
        builder (dot-builder-if builder :sslParameters ssl-parameters)
        builder (dot-builder-if builder :version to-version version)]
    (->HttpClient
      (.build builder)
      (or request-interceptors default-request-interceptors)
      (or response-interceptors default-response-interceptors))))

(defn request [{:keys [^java.net.http.HttpClient http-client
                       request-interceptors
                       response-interceptors]} r]
  (let [r                         (execute request-interceptors r)
        ^HttpRequest http-request (::request r)
        body-handler              (to-body-handler (or (:as r) :string))
        raw-response              (.send http-client http-request body-handler)]
    (if (:raw-response? r)
      raw-response
      (convert-raw-response raw-response))))


(defn get [^java.net.http.HttpClient client uri & [r]]
  (request client (merge {:method :get :uri uri} r)))

(comment
  (def client (http-client {:follow-redirects ::redirect-normal
                            :version          ::version-http-2}))


  (get-in (get client "https://www.google.com") [:headers "cache-control"])
  (def headers (:headers *1))
  (.firstValue headers "Cache-Control")
  (.get (.map headers) "status")
  (map identity (.map headers))

  (apply sorted-map-by String/CASE_INSENSITIVE_ORDER ["a" 1])
  )
