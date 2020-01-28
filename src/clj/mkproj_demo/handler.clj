(ns mkproj-demo.handler
  (:require
   [mkproj-demo.api                :as api]
   [chord.http-kit                 :refer [wrap-websocket-handler]]
   [clojure.core.async             :refer [go <! >! put! close!]]
   [compojure.core                 :refer [defroutes GET POST]]
   [compojure.route                :refer [resources not-found]]
   [ring.middleware.defaults       :refer [wrap-defaults api-defaults]]
   [ring.middleware.resource       :refer [wrap-resource]]
   [ring.middleware.session        :refer [wrap-session]]
   [ring.middleware.not-modified   :refer [wrap-not-modified]]
   [ring.middleware.content-type   :refer [wrap-content-type]]
   [ring.middleware.session.cookie :refer [cookie-store]]
   [ring.util.response             :refer [content-type resource-response]]))

(defn castra
  "Receives RPC request and calls it in api namespace."
  [{:keys [ws-channel] :as req}]
  (go
    (let [{:keys [message error]} (<! ws-channel)
          {:keys [type f args]} message
          result (apply (ns-resolve 'mkproj-demo.api f) args)]
      (if error
        (println "Error ocurred:" error)
        (if (= type :rpc)
          (>! ws-channel result)
          (>! ws-channel "Hello client from server!"))))))

(defroutes app-routes
  (GET "/" req
    (-> "public/index.html"
        (resource-response)
        (content-type "text/html")))
  (GET "/ws" [] (-> #'castra
                    (wrap-websocket-handler {:format :transit-json})))
  (resources "/")
  (not-found "Oups! This page doesn't exist! (404 error)"))

(def app
  (-> app-routes
      (wrap-session {:store (cookie-store {:key "a 16-byte secret"})})
      (wrap-defaults api-defaults)
      (wrap-resource "public")
      (wrap-resource "public")
      (wrap-content-type)
      (wrap-not-modified)))
