(ns codesmith.http.client.interceptor.others
  (:require [codesmith.http.client.interceptor.request :as ireq]
            [codesmith.http.client.interceptor.response :as ires]))

(deftype MeasuringInterceptor []
  ireq/RequestInterceptor
  (enter [_ request-map]
    (assoc request-map ::measuring-start (System/nanoTime)))
  (leave! [_ _ _])

  ires/ResponseInterceptor
  (leave [_ {:keys [::measuring-start]} _ response-map]
    (let [measuring-end (System/nanoTime)]
      (assoc response-map
        ::measuring-start measuring-start
        ::measuring-end measuring-end
        ::time-elpased (- measuring-end measuring-start)))))

(def measuring-interceptor (->MeasuringInterceptor))

(comment
  (satisfies? ires/ResponseInterceptor measuring-interceptor)

  (ires/leave measuring-interceptor {::measuring-start 100023123123} nil nil)

  )