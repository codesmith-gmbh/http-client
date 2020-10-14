(ns codesmith.http.client.interceptor.auth
  (:require [codesmith.http.client.interceptor.request :as ireq])
  (:import [java.util Base64]))

(defn encode-base64 [^String text]
  (.encodeToString (Base64/getEncoder) (.getBytes text)))

(defn basic-authentication-interceptor [username password]
  (ireq/->AuthenticationHeaderInterceptor (str "Basic " (encode-base64 (str username ":" password)))))

(defprotocol TokensStorage
  (access-token [self])
  (store-access-token! [self new-token])

  (refresh-token [self])
  (store-refresh-token! [self new-token]))

(defn oauth-authentication-interceptor [refreshing-client tokens-storage]
  (ireq/->AuthenticationHeaderInterceptor (str "Bearer " (access-token tokens-storage))))