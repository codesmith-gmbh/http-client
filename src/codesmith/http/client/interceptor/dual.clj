(ns codesmith.http.client.interceptor.dual
  (:require [codesmith.http.client.interceptor.protocols :as proto]))

(deftype MeasuringInterceptor []
  proto/RequestInterceptor
  (enter [_ request-map]
    (assoc request-map ::measuring-start (System/nanoTime)))
  (leave! [_ _ _])

  proto/ResponseInterceptor
  (leave [_ {:keys [::measuring-start]} _ response-map]
    (let [measuring-end (System/nanoTime)]
      (assoc response-map
        ::measuring-start measuring-start
        ::measuring-end measuring-end
        ::time-elpased (- measuring-end measuring-start)))))

(def measuring-interceptor (->MeasuringInterceptor))
