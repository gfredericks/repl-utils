(ns com.gfredericks.repl.bootstrap
  "Functions for setting up a namespace how I like it.

  Requiring this namespace will define a var clojure.core/&bs for
  quick bootstrapping from uncustomized namespaces.")

(def requires
  ;; excluding dir because we provide a better version in
  ;; com.gfredericks.repl
  '[[clojure.repl :refer :all :exclude [dir]]
    [com.gfredericks.repl :refer :all]])

(defn bootstrap-ns
  []
  (apply require requires))


(binding [*ns* (the-ns 'clojure.core)]
  (eval '(def &bs
           "A function installed by com.gfredericks.repl.bootstrap
           that refers repl-utility stuff into the current namespace."
           #'com.gfredericks.repl.bootstrap/bootstrap-ns)))
