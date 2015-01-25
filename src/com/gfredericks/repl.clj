(ns com.gfredericks.repl
  "My repl utilities."
  (:require [cemerick.pomegranate :as pom]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [clojure.tools.analyzer.passes.emit-form :as e]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.analyzer.jvm :as ctajvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :as j.e]
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
;;; Decent recursive macroexpansion, thanks to Nicola Mometto
;;;

(defn ^:private emit-form
  ([ast]
     (binding [e/-emit-form* emit-form]
       (emit-form ast {})))
  ([ast opts]
     (if (= :var (:op ast))
       (let [{:keys [env form]} ast]
         (if (namespace form)
           (let [sym (symbol (name form))]
             (if (= (get-in (env/deref-env) [:namespaces (:ns env) :mappings sym])
                    (resolve form))
               sym
               (let [aliases (get-in (env/deref-env) [:namespaces (:ns env) :aliases])]
                 (if-let [alias ((zipmap (vals aliases) (keys aliases)) (symbol (namespace form)))]
                   (symbol (name alias) (name form))
                   form))))
           form))
       (j.e/-emit-form ast opts))))

(defn mexpand-all
  "Like clojure.walk/macroexpand-all, but does things the right way
  and uses shorter symbols when possible for readability."
  ([form] (mexpand-all form *ns*))
  ([form ns]
     (binding [*ns* (the-ns ns)]
       (env/ensure (ctajvm/global-env)
                   (emit-form (ctajvm/analyze form))))))


;;;
;;; Slightly better pprint stuff
;;;

(defn ^:private canonize
  [ob]
  (walk/postwalk (fn [x]
                   (cond (map? x)
                         (into (sorted-map) x)

                         (set? x)
                         (into (sorted-set) x)

                         :else
                         x))
                 ob))

(defn pp
  "Combines functionality of clojure.pprint/pprint and clojure.pprint/pp,
  but also canonizes the object before printing."
  ([] (pp *1))
  ([x] (pprint/pprint (canonize x))))

;;;
;;; Running things in the background
;;;

(defonce bg-id-counter (atom -1))

(defn ^:private now [] (System/currentTimeMillis))

(defmethod print-method ::bg
  [var pw]
  (let [{:keys [start-time end-time state name]} (meta var)
        msg (case state
              :running (str name " has been running for "
                            (util/time-str (- (now) start-time)))
              :error (str "ERROR(" (.getMessage (deref var)) "): " name " ran for "
                          (util/time-str (- end-time start-time)))
              :done (str "DONE: " name " ran for " (util/time-str (- end-time start-time))))]
    (doto pw
      (.write "#<")
      (.write msg)
      (.write ">"))))

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
              (try (let [res (func)
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
      (let [t (doto (Thread. (bound-fn [] (go)))
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
