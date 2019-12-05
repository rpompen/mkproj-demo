(ns mkproj-demo.rpc
  (:require-macros
   [javelin.core :refer [defc defc= cell=]]
   [cljs.core.async.macros :refer [go go-loop]])
  (:require
   [mkproj-demo.shared :refer [port]]
   [javelin.core :refer [cell cell-let dosync]]
   [cljs-http.client :as http]
   [chord.client :refer [ws-ch]]
   [cljs.core.async :refer [<! >! put! chan close!]]))

;; CouchDB connection
(def db-server "127.0.0.1")
(def db-port 5984)
(def db-name "sample")

;; RPC launcher
(defc result nil)
(defc error nil)
(defc loading [])

(defn launch-fn 
  "Launches RPC call for `f` in backend. Return value goes into cell."
  [f cl & args]
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch (str "ws://localhost:" port "/ws")
                                                {:format :transit-json}))]
      (if error
        (js/console.log "Error:" (pr-str error))
        (do
          (>! ws-channel {:type :rpc :f f :args args})
          (reset! cl (:message (<! ws-channel)))))
      (close! ws-channel))))

(defc uuids nil)
(defn get-uuid
  "Returns `n` or 1 UUIDs in a vector."
  [& n]
  (go (let [result
            (<! (http/get (str "http://" db-server ":" db-port "/_uuids"
                               (when (some? n) (str "?count=" (first n))))))]
        (reset! uuids (:uuids (:body result))))))

(defn query
  "Fire Mango query to CouchDB.
   JSON query `m` will be sent to DB. Result gets merged into cell `cl`.

   An optional :map-fn and then :filter-fn are applied 
   to the result set."
  [m cl & {:keys [map-fn filter-fn agg-fn]}]
  (go
    (let [result
          (<! (http/post (str "http://" db-server ":" db-port "/" db-name "/_find")
                         {:json-params m}))]
      (if (:success result)
        (reset! cl (cond->> result
                     true :body
                     true :docs
                     map-fn (mapv map-fn)
                     filter-fn (filterv filter-fn)
                     agg-fn agg-fn))
        (reset! error (-> result :body))))))

;; segmented state + lenses
;; reduces load due to state modifications and allows easier refactoring
(defonce state
  (cell {}))

(defc= file-data (:file-data state) #(swap! state assoc :file-data %))
(defc= db-data   (:db-data state)   #(swap! state assoc :db-data %))

;; RPC to backend
;; Cell data is overwritten, not merged
(def get-file (partial launch-fn 'get-file file-data))

;; Database
;; Uses aggregate function to return hash-map, as required for state-merging
(def get-db (fn [] (query {"selector"
                            {"type" "person"}
                           "fields" ["name" "age"]}
                          db-data)))
