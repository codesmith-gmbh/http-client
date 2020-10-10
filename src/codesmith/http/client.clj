(ns codesmith.http.client
  (:require [clojure.string :as str]
            [clojure.core.reducers :as r])
  (:import [java.net.http HttpClient HttpClient$Version HttpRequest HttpResponse$BodyHandlers HttpClient$Redirect HttpResponse HttpRequest$BodyPublisher HttpRequest$BodyPublishers HttpRequest$Builder]
           [java.net URI]
           [clojure.lang Keyword]
           [java.io File]
           [java.nio.charset Charset])
  (:refer-clojure :exclude [get]))

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

(defmacro dot-builder-if
  ([builder method value]
   `(if-let [value# ~value]
      (. ~builder ~(symbol method) value#)
      ~builder))
  ([builder method to-fn value]
   `(if-let [value# (~to-fn ~value)]
      (. ~builder ~(symbol method) value#)
      ~builder)))

(defn http-client ^HttpClient [& [{:keys [authenticator
                                          connect-timeout
                                          cookie-handler
                                          executor
                                          follow-redirects
                                          priority
                                          proxy
                                          ssl-context
                                          ssl-parameters
                                          version]}]]
  (let [builder (HttpClient/newBuilder)
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
    (.build builder)))

(set! *warn-on-reflection* true)

(defn string-body [string encoding]
  (HttpRequest$BodyPublishers/ofString string (to-charset encoding)))

(defn build-request ^HttpRequest [{:keys [method uri body content-type accept headers as]}]
  (let [builder (HttpRequest/newBuilder)
        builder (case method
                  :get (.GET builder)
                  :put (.PUT builder (to-body-publisher body))
                  :post (.POST builder (to-body-publisher body))
                  :delete (.DELETE builder)
                  (.method builder (to-method method) (to-body-publisher body)))
        builder (dot-builder-if builder :uri to-uri uri)]
    (.build builder)))

(defn raw-request ^HttpResponse [^HttpClient client ^HttpRequest request]
  (.send client request (HttpResponse$BodyHandlers/ofString)))

(defn request [^HttpClient client r]
  (raw-request client (build-request r)))

(comment
  (def client (http-client {:follow-redirects ::redirect-normal
                            :version          ::version-http-2}))

  (raw-request client
               (build-request {:method :head :uri "https://google.com"}))

  (to-body-publisher nil)

  (to-method :get)

  )


(defn get [^HttpClient client uri & [r]]
  (request client (merge {:method :get :uri uri} r)))

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

(defn leave-identity [_ m]
  m)

(defprotocol Interceptor
  (enter [self request-map])
  (leave! [self ^HttpRequest$Builder builder request-map]))

(deftype FunctionInterceptor [enter leave!]
  Interceptor
  (enter [self request-map]
    (if-let [enter (.-enter self)]
      (enter request-map)
      request-map))
  (leave! [self builder request-map]
    (when-let [leave! (.-leave_BANG_ self)]
      (leave! builder request-map))))

(deftype HeadersInterceptor []
  Interceptor
  (enter [_ {:keys [accept content-type headers] :as request-map}]
    (assoc request-map :headers (assoc-if headers
                                          "Accept" accept
                                          "Content-Type" content-type)))
  (leave! [_ builder {:keys [headers]}]
    (set-headers builder headers)))

(deftype UriInterceptor []
  Interceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [uri]}]
    (.uri ^HttpRequest$Builder builder (to-uri uri))))

(deftype MethorInterceptor []
  Interceptor
  (enter [_ request-map] request-map)
  (leave! [_ builder {:keys [method body]}]
    (case method
      :get (.GET ^HttpRequest$Builder builder)
      :put (.PUT ^HttpRequest$Builder builder (to-body-publisher body))
      :post (.POST ^HttpRequest$Builder builder (to-body-publisher body))
      :delete (.DELETE ^HttpRequest$Builder builder)
      (.method ^HttpRequest$Builder builder (to-method method) (to-body-publisher body)))))

(deftype JsonInterceptor []
  Interceptor
  (enter [_ request-map]
    (assoc request-map :as :json
                       :accept "application/json"
                       :content-type "application/json"))
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


(comment
  (execute {::enter-interceptors [uri-interceptor headers-interceptor json-interceptor]
            :uri                 "https://www.google.com"})
  (def request-map (execute [uri-interceptor method-interceptor headers-interceptor json-interceptor]
                            {:uri    "https://www.google.com"
                             :method :post}))
  (.method (::request request-map))
  (.bodyPublisher (::request request-map))

  )