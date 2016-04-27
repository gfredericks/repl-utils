(ns com.gfredericks.repl
  "My repl utilities."
  (:refer-clojure :exclude [comment])
  (:require [cemerick.pomegranate :as pom]
            [clojure.java.classpath :as cp]
            [clojure.pprint :as pprint]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as ns]
            [clojure.walk :as walk]))

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
  "Like clojure.repl/dir but also works with local aliases. If no arg is
  given, defaults to current namespace."
  ([]
   `(dir ~(.name *ns*)))
  ([ns-name-or-alias]
   (list `clojure.repl/dir
         (if-let [ns (get (ns-aliases *ns*) ns-name-or-alias)]
           (.getName ns)
           ns-name-or-alias))))

;;;
;;; Enhanced version of clojure.core/comment
;;;

(defmacro comment
  "Like clojure.core/comment, but allows uncommenting top-level
  forms with ~, e.g.:

  (repl/comment
    (don't do this)
    (or this)
    ~(but do this)
    ~(and this)
    (but not this))"
  [& args]
  (->> args
       (filter #(and (seq? %)
                     (= 'clojure.core/unquote (first %))))
       (map second)
       (cons 'do)))

;;;
;;; Slightly better pprint stuff
;;;

(defn ^:private canonize
  [ob]
  (walk/postwalk (fn [x]
                   (cond (map? x)
                         (try
                           (into (sorted-map) x)
                           (catch ClassCastException _
                             x))

                         (set? x)
                         (try
                           (into (sorted-set) x)
                           (catch ClassCastException _
                             x))

                         :else
                         x))
                 ob))

(defn pp
  "Combines functionality of clojure.pprint/pprint and clojure.pprint/pp,
  but also canonizes the object before printing."
  ([] (pp *1))
  ([x] (pprint/pprint (canonize x))))

;;
;; Debugging with locals-access
;;

(defmacro locals
  []
  (let [names (keys &env)]
    (zipmap (map #(list 'quote %) names) names)))

(defmacro throw-locals
  ([] `(throw-locals "Throwing locals"))
  ([msg] `(throw (ex-info ~msg (locals)))))

;;
;; Interrupt-friendly infinite loop
;;

(defmacro forever
  "Executes body repeatedly, watching for thread interrupts."
  [& body]
  `(while (not (Thread/interrupted))
     ~@body))

;;
;; Running all the tests
;;

(defn run-all-tests
  "Like clojure.test/run-all-tests, but also requires namespaces first."
  []
  (let [nses (->> (cp/classpath)
                  (remove #(re-find #"\.jar" (str %)))
                  (ns/find-namespaces))]
    (doseq [ns nses] (require ns))
    (let [nses-with-tests
          (filter (fn [ns]
                    (some #(:test (meta %))
                          (vals (ns-publics ns))))
                  nses)]
      (apply test/run-tests nses-with-tests))))
