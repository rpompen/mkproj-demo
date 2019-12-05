(ns mkproj-demo.core
  (:require [hoplon.core :as h :refer [defelem div h1 br p text pre input
                                       button label ul li nav form with-init!
                                       with-timeout if-tpl for-tpl strong
                                       case-tpl span a small]]
            [javelin.core :as j :refer [cell cell= defc defc= cell-let dosync]]
            [mkproj-demo.rpc :as rpc]))

(defelem main [_ _]
  (div :id "app"
       (h1 "Sample")
       (p "Gets data from file via JVM backend. "
          (button :type "button" :click #(rpc/get-file) "Get file"))
       (p (text "~{rpc/file-data}"))
       (p "Gets items from CouchDB via REST interface. "
          (button :type "button" :click #(rpc/get-db) "Get DB"))
       (p (text "~{rpc/db-data}"))))

(.replaceChild (.-body js/document)
               (main) (.getElementById js/document "app"))
