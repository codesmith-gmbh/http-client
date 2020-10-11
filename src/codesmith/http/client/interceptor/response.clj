(ns codesmith.http.client.interceptor.response
  (:require [clojure.core.reducers :as r])
  (:import [java.net.http HttpResponse HttpHeaders]
           [java.util Map$Entry List]))

(defprotocol ResponseInterceptor
  (leave [self request-map response response-map]))

(defn execute-leave [interceptors request-map response]
  (let [response-map (r/reduce (fn [response-map interceptor]
                                 (leave interceptor request-map response response-map))
                               {}
                               interceptors)]
    (assoc response-map ::raw-response response)))

(deftype StatusInterceptor []
  ResponseInterceptor
  (leave [_ _ response response-map]
    (assoc response-map :status (.statusCode ^HttpResponse response))))

(deftype BodyInterceptor []
  ResponseInterceptor
  (leave [_ _ response response-map]
    (assoc response-map :body (.body ^HttpResponse response))))

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

(deftype HeadersFullConversionInterceptor []
  ResponseInterceptor
  (leave [_ _ response response-map]
    (assoc response-map :headers (convert-headers (.headers ^HttpResponse response)))))

(deftype HeadersTreeMapInterceptor []
  ResponseInterceptor
  (leave [_ _ response response-map]
    (assoc response-map :headers (.map (.headers ^HttpResponse response)))))

(def status-interceptor (->StatusInterceptor))
(def body-interceptor (->BodyInterceptor))
(def headers-full-conversion-interceptor (->HeadersFullConversionInterceptor))
(def headers-tree-map-interceptor (->HeadersTreeMapInterceptor))