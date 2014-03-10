(ns com.gfredericks.repl
  "My repl utilities."
  (:require [cemerick.pomegranate :as pom]))

;;;
;;; Bootstrapping
;;;

(defn bootstrap-ns
  []
  ;; excluding dir because we provide a better version in this
  ;; namespace
  (require '[clojure.repl :refer :all :exclude [dir]]
           '[com.gfredericks.repl :refer :all]))


(binding [*ns* (the-ns 'clojure.core)]
  (eval '(def &bs
           "A function installed by com.gfredericks.repl that refers
           repl-utility stuff into the current namespace."
           com.gfredericks.repl/bootstrap-ns)))

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
  (let [min (/ ms-delta 60000.0)]
    (if (>= min 60)
      (format "%.2f hours" (/ min 60))
      (format "%.2f minutes" min))))

(defn ^:private now [] (System/currentTimeMillis))

(defmethod print-method ::bg
  [{:keys [start-time end-time name ex state]} pw]
  (let [msg (case state
              :running (str name " has been running for "
                            (time-str (- (now) start-time)))
              :error (str "ERROR(" (.getMessage ex) "): " name " ran for "
                          (time-str (- end-time start-time)))
              :done (str "DONE: " name " ran for " (time-str (- end-time start-time))))]
    (.write pw (str "#<" msg ">"))))

(defn run-and-report
  [result-var status-var var-name func]
  (let [start-time (now)]
    (letfn [(go []
              (try (let [res (func)
                         end-time (now)]
                     (alter-var-root status-var assoc :state :done :end-time end-time)
                     (alter-var-root result-var (constantly res)))
                   (catch Throwable t
                     (let [end-time (now)]
                       (alter-var-root status-var assoc :state :error, :end-time end-time, :ex t)
                       (alter-var-root result-var (constantly t)))))
              (println @status-var))]
      (alter-var-root status-var (constantly
                                  (with-meta {:name var-name, :start-time start-time, :state :running}
                                    {:type ::bg})))
      (let [t (doto (Thread. (bound-fn [] (go)))
                (.start))]
        (alter-var-root status-var assoc :thread t)))))

(defmacro bg
  "Runs the body in a background thread.

  Creates two vars, one named bg<N> and the other bg<N>'.

  The first will eventually contain the result of the computation,
  or any exception that is thrown.

  The second is a map that prints with status & runtime information."
  [& body]
  (let [base (str "bg" (swap! bg-id-counter inc))
        sym (symbol base)
        sym' (symbol (str base \'))]
    `(do (println "Starting background task" '~sym)
         (def ~sym)
         (def ~sym')
         (run-and-report (var ~sym) (var ~sym') '~sym (fn [] ~@body))
         '~sym)))
