(ns mkproj-demo.api)

(defn get-file []
  (clojure.edn/read-string (slurp "data/items.edn")))
