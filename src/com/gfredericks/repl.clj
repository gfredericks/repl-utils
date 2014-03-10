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
  "Runs the body in a background thread, returning a symbol for
  a new var created that contains information about how long the
  code has been running.

  If it completes successfully, the var will be updated with the result.

  If an exception is thrown, the var will contain the exception."
  [& body]
  (let [sym (symbol (str "bg" (swap! bg-id-counter inc)))]
    `(do (println "Starting background task" '~sym)
         (def ~sym)
         (run-and-report (var ~sym) '~sym (fn [] ~@body))
         '~sym)))
