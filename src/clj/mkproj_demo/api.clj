(ns mkproj-demo.api)
(defn get-data []
  (clojure.edn/read-string (slurp "data/articles.clj")))

