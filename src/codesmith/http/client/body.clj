(ns codesmith.http.client.body
  (:require [codesmith.http.client.coercion :as c]
            [clojure.java.io :as io]
            [cheshire.core :as json])
  (:import [java.net.http HttpRequest$BodyPublishers HttpResponse$BodyHandlers]
           [java.io ByteArrayOutputStream]))

(defrecord SendAs [content-type body-publisher-for])

(def send-as-string
  (->SendAs nil
            (fn [{:keys [body]}]
              (HttpRequest$BodyPublishers/ofString body))))

(defn send-as-string-with
  [charset]
  (->SendAs nil
            (fn [{:keys [body]}]
              (HttpRequest$BodyPublishers/ofString body (c/to-charset charset)))))

(def send-as-json
  (->SendAs "application/json"
            (fn [{:keys [body form-params]}]
              (let [baos   (ByteArrayOutputStream.)
                    writer (io/writer baos)]
                (json/generate-stream (or form-params body) writer)
                (HttpRequest$BodyPublishers/ofByteArray (.toByteArray baos))))))

(defrecord AcceptAs [content-type body-handler body-transform])

(def accept-as-string
  (->AcceptAs nil
              (HttpResponse$BodyHandlers/ofString)
              identity))

(def accept-as-json
  (->AcceptAs "application/json"
              (HttpResponse$BodyHandlers/ofInputStream)
              (fn [is]
                (try
                  (json/parse-stream (io/reader is))
                  (finally
                    (.close is))))))
