(ns codesmith.http.client.utils)

(defmacro dot-builder-if
  ([builder method value]
   `(if-let [value# ~value]
      (. ~builder ~(symbol method) value#)
      ~builder))
  ([builder method to-fn value]
   `(if-let [value# (~to-fn ~value)]
      (. ~builder ~(symbol method) value#)
      ~builder)))

(defn assoc-if
  ([m k v]
   (if v (assoc m k v) m))
  ([m k1 v1 k2 v2]
   (assoc-if (assoc-if m k1 v1) k2 v2)))
