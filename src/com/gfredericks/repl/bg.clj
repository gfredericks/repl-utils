(ns com.gfredericks.repl.bg
  (:require [com.gfredericks.repl.bg.util :as util])
  (:import (java.io Writer)))

(defonce bg-id-counter (atom -1))

(defn ^:private now [] (System/currentTimeMillis))

(defn ^:private throwable->message
  "Returns a (possibly abbreviated) message."
  [^Throwable ex]
  (let [s (.getMessage ex)]
    (if (< 140 (count s))
      (str (subs s 0 137) "...")
      s)))

(defmethod print-method ::bg
  [var ^Writer pw]
  (let [{:keys [start-time end-time state name]} (meta var)
        msg (case state
              :running (str name " has been running for "
                            (util/time-str (- (now) start-time)))
              :error (str "ERROR(" (throwable->message (deref var)) "): " name " ran for "
                          (util/time-str (- end-time start-time)))
              :done (str "DONE: " name " ran for " (util/time-str (- end-time start-time))))]
    (doto pw
      (.write "#<")
      (.write msg)
      (.write ">"))))

(def ^:private ^:dynamic ^clojure.lang.Var *var*)

(defn run-and-report
  [var func]
  (let [var-name (-> var meta :name)
        start-time (now)
        p (promise)]
    (alter-meta! var assoc
                 :start-time start-time
                 :state :running
                 :future (util/promise->future p))
    (letfn [(go []
              (try (let [res (binding [*var* var] (func))
                         end-time (now)]
                     (doto var
                       (alter-meta! assoc :state :done, :end-time end-time)
                       (alter-var-root (constantly res)))
                     (deliver p {:val res}))
                   (catch Throwable t
                     (let [end-time (now)]
                       (doto var
                         (alter-meta! assoc :state :error, :end-time end-time)
                         (alter-var-root (constantly t)))
                       (deliver p {:err t}))))
              (println var))]
      (let [f (bound-fn [] (go))
            t (doto (Thread. ^Runnable f)
                (.start))]
        (alter-meta! var assoc :thread t)))))

(defmacro bg
  "Runs the body in a background thread, and creates a var for the
  eventual result. The var will have the optionally supplied name
  or else will be named bg<N>. Logs to *out* when the background
  task starts and finishes.

  When the body finishes, the var will contain either the result, or
  any exception that is thrown.

  E.g.,

    (bg foo (slurp file)) ;; => #'foo
    (bg (slurp file)) ;; => #'bg0

  The var also contains some useful metadata:

    :form   - the code of the bg call
    :state  - one of #{:running :done :error}
    :future - a future-like object that can be derefenced, will
              block, and will throw an exception when appropriate
    :thread - the thread object running the code"
  [& args]
  (let [[sym body] (if (and (symbol? (first args)) (seq (rest args)))
                     [(first args) (rest args)]
                     [(symbol (str "bg" (swap! bg-id-counter inc))) args])]
    `(do (println "Starting background task" '~sym)
         (doto (def ~sym)
           (alter-meta! assoc :form '~&form :type ::bg)
           (run-and-report (^:once fn* [] ~@body))))))

(defmacro bg-deref
  "Deref's the given bg object's future. Blocks and returns the value,
  or throws an exception if the bg threw an exception."
  [bg-name]
  `(-> (var ~bg-name) meta :future deref))

(defn search*
  [key-fn seq]
  (alter-var-root *var* (constantly clojure.lang.PersistentQueue/EMPTY))
  (alter-meta! *var* assoc :search {:total 0, :max-results 10})
  (let [start-time (System/currentTimeMillis)]
    (try
      (loop [best-so-far      nil
             total            0
             seq              seq
             till-next-update 1]
        (if-let [[x & xs] (clojure.core/seq seq)]
          (let [k (key-fn x)
                best? (or (nil? best-so-far) (< best-so-far k))
                total' (inc total)]
            (when best?
              (printf "New best for %s! (%s)\n" (.sym *var*) k)
              (let [max-results (-> *var* meta :search :max-results)]
                (alter-var-root *var*
                                (fn [q]
                                  (loop [q' (conj q x)]
                                    (if (< max-results (count q'))
                                      (recur (pop q'))
                                      q'))))))

            (recur (if best? k best-so-far)
                   total'
                   xs
                   (if (or best? (zero? till-next-update))
                     (let [now (System/currentTimeMillis)
                           frequency-hz (/ (double total) (- now start-time))]
                       (alter-meta! *var* update :search
                                    (fn [m]
                                      (assoc m
                                             :total total'
                                             :frequency-hz frequency-hz)))
                       ;; aim to update about once a second
                       (Math/round frequency-hz))
                     (dec till-next-update))))
          @*var*))
      (catch Throwable t
        (printf "Error in %s!\n" (.sym *var*))
        (vary-meta @*var* assoc :caught t)))))

(defmacro bg-search
  "Given a key-fn and a lazy seq, runs a bg process that updates the
  given var in the background with the best elements of seq (according
  to key-fn) seen so far."
  [key-fn seq]
  `(bg (search* ~key-fn ~seq)))
