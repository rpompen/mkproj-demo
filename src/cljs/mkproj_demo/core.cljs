(ns mkproj-demo.core
  (:require [hoplon.core :as h :refer [defelem div h1 br p text pre input
                                       button label ul li nav form with-init!
                                       with-timeout if-tpl for-tpl strong
                                       case-tpl span a small fieldset]]
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
       (p (for-tpl [{id :_id name :name age :age} rpc/db-data]
                   (fieldset
                    (input :type "text" :value name :disabled true)
                    (input :type "text" :value age :disabled true)
                    (button :type "button" :click #(rpc/del-db @id) "Erase"))))
       (p (fieldset 
           (let [name (cell nil)
                 age (cell nil)]
            [(label "New person")(br)
             (input :type "text" :placeholder "name"
                    :keyup #(reset! name (.. % -target -value))
                    :focus #(set! (.. % -target -value) @name))
             (input :type "number" :placeholder "age"
                    :keyup #(reset! age (int (.. % -target -value)))
                    :focus #(set! (.. % -target -value) @age))
             (button :type "button"
                     :click (fn []
                              (rpc/add-db @name @age)
                              (reset! name nil)
                              (reset! age nil))
                     "Add")])))))

(.replaceChild (.-body js/document)
               (main) (.getElementById js/document "app"))
