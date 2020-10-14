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

(defmacro some-assoc-> [m & kvs]
  (let [kvs-count (count kvs)]
    (when (odd? kvs-count)
      (throw (IllegalArgumentException. "Expecting pairs of arguments")))
    (let [value-count (/ kvs-count 2)
          vs          (mapv #(gensym (str "v" % "_")) (range value-count))
          lets        (mapcat (fn [[_ ve] v]
                                [v ve]) (partition 2 kvs) vs)
          assocs      (mapcat (fn [[ke _] v]
                                [v `(assoc ~ke ~v)]) (partition 2 kvs) vs)]
      `(let [~@lets]
         (cond-> ~m
                 ~@assocs)))))
