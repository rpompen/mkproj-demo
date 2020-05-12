(ns mkproj-demo.api
  (:require [clojure.edn :as edn]))

(defn get-file []
  (edn/read-string (slurp "data/items.edn")))
