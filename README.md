# Codesmith Http Client

**This library is in ALPHA release; expect API breaking changes.**

A Clojure Wrapper for the JRE 11 HttpClient.

## Usage

To send requests, an explicit client has to be created. The requests functions takes a logging context (from
[codesmith.logger](https://clojars.org/codesmith/logger)) as first argument, the client as second argument and specification
for the request as other arguments. Logging is done on debug level; the context is used to situate the log entries.

```clojure
(ns example
    (:require [codesmith.http.client :as http]))

(def client
  "Creates a client for a JSON Rest API located at api.server.com with basic authentication"
  (http/client :send-receive :json
               :auth {:basic "password"}
               :http-prefix "https://api.server.com"))

(http/get {:activity "activity1"} client "/server_info")
;; {....}
```
