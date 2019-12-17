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
(def urls (str "http://" db-server ":" db-port "/"))
(def urld (str urls db-name))
(def urlq (str urld "/_find"))
;; Merge with :json-params for authentication
(def db-auth {:basic-auth {:username "admin"
                           :password "Cl0jure!"}})

;; RPC launcher
(defc error nil)

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

;;; UUID generator of CouchDB
(defc uuids nil)

(defn get-uuid
  "Returns `n` or 1 UUIDs in a vector."
  [& n]
  (go (let [result
            (<! (http/get (str "http://" db-server ":" db-port "/_uuids"
                               (when (some? n) (str "?count=" (first n))))))]
        (reset! uuids (:uuids (:body result))))))

;;; CRUD: CREATE RETRIEVE UPDATE DELETE
;;; CREATE
(defn doc-add
  "Add document to CouchDB and run callback for refresh."
  [m cb]
  (go (let [uuid (-> (<! (http/get (str urls "/_uuids"))) :body :uuids first)
            result (<! (http/put (str urld "/" uuid)
                                 {:json-params m}))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;;; RETRIEVE
(defn query
  "Fire Mango query to CouchDB.
   JSON query `m` will be sent to DB. Result gets merged into cell `cl`.
   An optional funtion `:func` is applied to the result set."
  [m cl & {func :func}]
  (go
    (let [result
          (<! (http/post urlq {:json-params m}))]
      (if (:success result)
        (reset! cl (cond->> result
                     true :body
                     true :docs
                     func func))
        (reset! error (-> result :body))))))

;;; UPDATE
(defn doc-update
  "Update document in CouchDB and run callback for refresh."
  [id m cb]
  (go (let [old (-> (<! (http/post urlq {:json-params {"selector" {"_id" id}}}))
                    :body :docs first)
            result (-> (<! (http/put (str urld "/" id)
                                     {:json-params (merge old m)})))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;;; DELETE
(defn doc-delete
  "Delete document in CouchDB and run callback for refresh."
  [id cb]
  (go (let [rev (-> (<! (http/post urlq {:json-params {"selector" {"_id" id}}}))
                    :body :docs first :_rev)
            result (<! (http/delete (str urld "/" id "?rev=" rev)))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

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
(defn get-db [] (query {"selector"
                        {"type" "person"}}
                       db-data :func (partial sort-by :name)))

(defn add-db [name age] (doc-add {"type" "person"
                                  "name" name
                                  "age" age} get-db))

(defn del-db [id] (doc-delete id get-db))
