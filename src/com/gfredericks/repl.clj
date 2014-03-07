(ns com.gfredericks.repl
  "My repl utilities.")

(defonce bg-id-counter (atom -1))

(defn ^:private time-str
  [ms-delta]
  (let [min (/ ms-delta 60000.0)]
    (if (>= min 60)
      (format "%.2f hours" (/ min 60))
      (format "%.2f minutes" min))))

(defn ^:private now [] (System/currentTimeMillis))

(defmethod print-method ::bg
  [{:keys [start-time name]} pw]
  (.write pw (str "#<" name " has been running for "
                  (time-str (- (now) start-time))
                  ">")))

(defn ^:private set-val-and-meta
  [var val meta]
  (alter-var-root var (constantly val))
  (alter-meta! var merge meta))

(defn run-and-report
  [var var-name func]
  (let [start (now)]
    (letfn [(go []
              (try (let [res (func)
                         runtime (- (now) start)]
                     (set-val-and-meta var res {::runtime runtime})
                     (println "Background job" var-name "finished in" (time-str runtime)))
                   (catch Throwable t
                     (let [runtime (- (now) start)]
                       (set-val-and-meta var t {::runtime runtime})
                       (println (str "Error in background job "
                                     var-name
                                     "! (ran for"
                                     (time-str runtime)
                                     ")"))
                       (println t)))))]
      (let [t (doto (Thread. (bound-fn [] (go)))
                (.start))]
        (set-val-and-meta var
                          (with-meta {:name var-name, :thread t, :start-time start}
                            {:type ::bg})
                          {::thread t})))))

(defmacro bg
  "Runs code in a future, defs the future to a var, and prints a
  message when it finishes."
  [& body]
  (let [sym (symbol (str "bg" (swap! bg-id-counter inc)))]
    ;; TODO: make the future somehow print how long it's been running
    ;; when you print it.
    `(do (println "Starting background task" '~sym)
         (def ~sym)
         (run-and-report (var ~sym) '~sym (fn [] ~@body))
         '~sym)))

(defn bootstrap-ns
  []
  (require '[clojure.repl :refer :all]
           '[com.gfredericks.repl :refer :all]))

(binding [*ns* (the-ns 'clojure.core)]
  (eval '(def &bs
           "Pulls repl-utility stuff into this namespace."
           com.gfredericks.repl/bootstrap-ns)))
