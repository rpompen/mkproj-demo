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
;; Creates a closure for pagination state
(defn query
  "Build a Mango query to CouchDB as a closure retaining page state. JSON query `m` will be sent to DB.
   Cell `cl` will contain result page. Cell `npg` will contain next available page number.
   Default page size is 25.

   The closure allows for previous pages to be requested, using `:page`.
   No argument returns first or most recent page. Setting `:next-page` returns next available page.
   Page list can be cleared by setting `:clear-pages` flag."
  ([m cl npg] (query m cl 25))
  ([m cl npg page-size]
   (let [pages (cell [])
         curpage (cell 0)] 
     (fn redo [& {:keys [page clear-pages next-page]}]
       (cond
         clear-pages (do (reset! pages []) (redo))
         :else (go
                 (let [_ (when (some? page) (reset! curpage page))
                       result (<! (http/post urlq {:json-params
                                                   (merge (cond
                                                            (and (some? page) (== 0 page)) m
                                                            (and (some? page) clear-pages) m
                                                            (some? page) (assoc m :bookmark (nth @pages (dec page)))
                                                            (seq @pages) (assoc m :bookmark (nth @pages (dec @curpage)))
                                                            :else m)
                                                          {:limit page-size})}))
                       next-page (-> result :body :bookmark)
                       next-page-number (if (or (contains? (set @pages) next-page) (< (count (-> result :body :docs)) page-size))
                                          (count @pages)
                                          (inc (count @pages)))]
                   (if (:success result)
                     (do
                       (when (and (== (count (-> result :body :docs)) page-size)
                                  (not (contains? (set @pages) next-page)))
                         (swap! pages conj next-page))
                       (reset! cl (-> result :body :docs))
                       (reset! npg next-page-number))
                     (reset! error (:body result))))))))))

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

(defc= file-data   (:file-data   state) #(swap! state assoc :file-data %))
(defc= people-page (:people-page state) #(swap! state assoc :people-page %))
(defc people-page-next 0)

;; RPC to backend
;; Cell data is overwritten, not merged
(defn get-file [] (launch-fn 'get-file file-data))

;; Database
(def people (query {"selector"
                    {"type" "person"}}
                   people-page people-page-next 4))

(defn add-db [name age] (doc-add {"type" "person"
                                  "name" name
                                  "age" age} #(people :clear-pages true)))

(defn del-db [id] (doc-delete id #(people :clear-pages true)))

(people)
