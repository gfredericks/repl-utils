(ns com.gfredericks.repl
  "My repl utilities.")

(defonce bg-id-counter (atom -1))

(defn run-and-report
  [var var-name func]
  (future (try (let [res (time (func))]
                 (println "Background job" var-name "finished")
                 (alter-var-root var (constantly res)))
               (catch Throwable t
                 (println (str "Error in background job " var-name "!"))
                 (println t)
                 (alter-var-root var (constantly t))))))

(defmacro bg
  "Runs code in a future, defs the future to a var, and prints a
  message when it finishes."
  [& body]
  (let [sym (symbol (str "bg" (swap! bg-id-counter inc)))]
    ;; TODO: make the future somehow print how long it's been running
    ;; when you print it.
    `(do (println "Starting background task" '~sym)
         (def ~sym (run-and-report (var ~sym) '~sym (fn [] ~@body))))))

(defn bootstrap-ns
  []
  (require '[clojure.repl :refer :all]
           '[com.gfredericks.repl :refer :all]))

(binding [*ns* (the-ns 'clojure.core)]
  (eval '(def &bs
           "Pulls repl-utility stuff into this namespace."
           com.gfredericks.repl/bootstrap-ns)))
