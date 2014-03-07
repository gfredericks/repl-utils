(ns com.gfredericks.repl
  "My repl utilities.")

(defn run-and-report
  [func]
  (future (try (time (func))
               (println "Finished!")
               (catch Throwable t
                 (println "Error!")
                 (throw t)))))

(defmacro bg
  "Runs code in a future, defs the future to a var, and prints a
  message when it finishes."
  [& body]
  (let [sym (symbol (format "r%02d" (rand-int 100)))]
    `(do (def ~sym (run-and-report (fn [] ~@body)))
         (println "Defined future as" '~sym))))
