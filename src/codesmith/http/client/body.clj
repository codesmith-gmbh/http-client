(ns codesmith.http.client.body
  (:require [codesmith.http.client.coercion :as c]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clojure.edn :as edn])
  (:import [java.net.http HttpRequest$BodyPublishers HttpResponse$BodyHandlers HttpRequest HttpResponse]
           [java.io ByteArrayOutputStream PushbackReader]))

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

(def send-as-edn
  (->SendAs "application/edn"
            (fn [{:keys [body form-params]}]
              (let [baos (ByteArrayOutputStream.)
                    writer (io/writer baos)]
                (binding [*out* writer]
                  (pr (or form-params body)))
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
                  (json/parse-stream-strict (io/reader is) true)
                  (finally
                    (.close is))))))

(def accept-as-edn
  (->AcceptAs "application/edn"
              (HttpResponse$BodyHandlers/ofInputStream)
              (fn [is]
                (try
                  (edn/read (PushbackReader. (io/reader is)))
                  (finally
                    (.close is))))))