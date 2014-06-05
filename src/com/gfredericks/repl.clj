(ns com.gfredericks.repl
  "My repl utilities."
  (:require [cemerick.pomegranate :as pom]
            [com.gfredericks.repl.util :as util]))

;;;
;;; Dependencies
;;;

(defmacro add-dep
  "E.g.: (add-dep [com.gfredericks/z \"0.1.0\"])"
  [lein-mvn-coords]
  `(pom/add-dependencies :coordinates ['~lein-mvn-coords]
                         :repositories
                         (merge cemerick.pomegranate.aether/maven-central
                                {"clojars" "http://clojars.org/repo"})))

;;;
;;; Enhanced versions of clojure.repl stuff
;;;

(defmacro dir
  "Like clojure.repl/dir but also works with local aliases."
  [ns-name-or-alias]
  (list `clojure.repl/dir
        (if-let [ns (get (ns-aliases *ns*) ns-name-or-alias)]
          (.getName ns)
          ns-name-or-alias)))

;;;
;;; Running things in the background
;;;

(defonce bg-id-counter (atom -1))

(defn ^:private time-str
  [ms-delta]
  (let [sec (/ ms-delta 1000.0)
        min (/ sec 60.0)]
    (cond (< sec 60)
          (format "%.3f seconds" sec)

          (< min 60)
          (format "%.2f minutes" min)

          :else
          (format "%.2f hours" (/ min 60)))))

(defn ^:private now [] (System/currentTimeMillis))

(defmethod print-method ::bg
  [fut pw]
  (let [{:keys [start-time end-time name ex state]} (meta fut)
        msg (case state
              :running (str name " has been running for "
                            (time-str (- (now) start-time)))
              :error (str "ERROR(" (.getMessage ex) "): " name " ran for "
                          (time-str (- end-time start-time)))
              :done (str "DONE: " name " ran for " (time-str (- end-time start-time))))]
    (.write pw (str "#<" msg ">"))))

(defn run-and-report
  [result-var status-var var-name func]
  (let [start-time (now)
        p (promise)]
    (letfn [(go []
              (try (let [res (func)
                         end-time (now)]
                     (deliver p {:val res})
                     (alter-var-root status-var vary-meta assoc :state :done :end-time end-time)
                     (alter-var-root result-var (constantly res)))
                   (catch Throwable t
                     (let [end-time (now)]
                       (deliver p {:err t})
                       (alter-var-root status-var vary-meta assoc :state :error, :end-time end-time, :ex t)
                       (alter-var-root result-var (constantly t)))))
              (println @status-var))]
      (alter-var-root status-var (constantly
                                  (with-meta (util/promise->future p)
                                    {:type ::bg, :name var-name, :start-time start-time, :state :running})))
      (let [t (doto (Thread. (bound-fn [] (go)))
                (.start))]
        (alter-var-root status-var vary-meta assoc :thread t)))))

(defmacro bg
  "Runs the body in a background thread.

  Creates two vars, one named bg<N> and the other bg<N>'.

  The first will eventually contain the result of the computation,
  or any exception that is thrown.

  The second is a future that prints with status & runtime information.
  When dereferenced, it will block until the body is complete, either
  returning its result or throwing the exception.

  Returns the name of the first var."
  [& body]
  (let [base (str "bg" (swap! bg-id-counter inc))
        sym (symbol base)
        sym' (symbol (str base \'))]
    `(do (println "Starting background task" '~sym)
         (def ~sym)
         (def ~sym')
         (run-and-report (var ~sym) (var ~sym') '~sym (fn [] ~@body))
         '~sym)))
