(ns mkproj-demo.rpc
  (:require-macros
   [javelin.core :refer [defc defc=]]
   [cljs.core.async.macros :refer [go]])
  (:require
   [mkproj-demo.shared :refer [port server db-port db-server db-name]]
   [javelin.core :refer [cell]]
   [cljs-http.client :as http]
   [chord.client :refer [ws-ch]]
   [cljs.core.async :refer [<! >! close!]]))

;; CouchDB connection
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
    (let [{:keys [ws-channel error]} (<! (ws-ch (str "ws://" server ":" port "/ws")
                                                {:format :transit-json}))]
      (if error
        (js/console.log "Error:" (pr-str error))
        (do
          (>! ws-channel {:type :rpc :f f :args args})
          (let [msg (:message (<! ws-channel))]
            (cond (= msg :castranil) (reset! cl nil)
                  (some? (:castraexpt msg)) (reset! mkproj-demo.rpc/error (:castraexpt msg))
                  :else (reset! cl msg)))))
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
                                 (merge {:json-params m} db-auth)))]
        (when-not (:success result)
          (reset! error (:body result)))
        (cb))))

;;; RETRIEVE
(defn query
  "Fire Mango query to CouchDB.
   JSON query `m` will be sent to DB. Result gets sent to cell `cl`.
   An optional funtion `:func` is applied to the result set.
   `page` is the page number to get. `pages` is a hash-map containing bookmarks.
   Initialize that map as nil."
  [m cl & {:keys [func page-size page pages] :or {func identity page-size 25 pages (cell :none)}}]
  (go
    (let [result
          (<! (http/post urlq
                         (merge {:json-params
                                 (merge m {:limit page-size
                                           :bookmark (if (or (nil? page)
                                                             (= page 0)) nil
                                                         (get-in @pages [:bookmarks page]))})}
                                db-auth)))
          next-bookmark (-> result :body :bookmark)]
      (when (= @pages {}) (reset! pages {:bookmarks {0 nil}}))
      (if (:success result)
        (do (reset! cl (-> result :body :docs func))
            (when (and (not= @pages :none)
                       (not (-> @pages :bookmarks vals set (contains? next-bookmark))))
              (swap! pages assoc-in [:bookmarks (inc page)]
                     next-bookmark))
            (when (not= @pages :none) (swap! pages assoc :curpage (or page 0))))
        (reset! error (:body result))))))

;;; UPDATE
(defn doc-update
  "Update document in CouchDB and run callback for refresh."
  [id m cb]
  (go (let [old (-> (<! (http/post urlq (merge {:json-params {"selector" {"_id" id}}}
                                               db-auth)))
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
  (go (let [rev (-> (<! (http/post urlq (merge {:json-params {"selector" {"_id" id}}}
                                               db-auth)))
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
(defc= people (:people state) #(swap! state assoc :people %))
(defc  people-pages {:bookmarks {0 nil}})

;; RPC to backend
;; Cell data is overwritten, not merged
(defn get-file [] (launch-fn 'get-file file-data))

;; Database

(defn get-people
  [& [page]]
  (query {"selector"
          {"type" "person"}
          "sort" [{"name" "asc"}]}
         people
         :func (partial sort-by :name)
         :page-size 4
         :pages people-pages
         :page  page))

(defn add-db [name age] (doc-add {"type" "person"
                                  "name" name
                                  "age" age} (fn []
                                               (reset! people-pages {:bookmarks {0 nil}})
                                               (js/setTimeout get-people 500))))

(defn del-db [id] (doc-delete id (fn []
                                   (reset! people-pages {:bookmarks {0 nil}})
                                   (js/setTimeout get-people 500))))

(get-people)
