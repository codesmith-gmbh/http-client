(ns codesmith.http.client.interceptor.protocols
  (:import [java.net.http HttpRequest$Builder]))

(defprotocol RequestInterceptor
  ""
  (enter [self request-map])
  (leave! [self ^HttpRequest$Builder builder request-map]))

(defprotocol ResponseInterceptor
  (leave [self request-map response response-map]))