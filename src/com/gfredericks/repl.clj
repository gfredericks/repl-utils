(ns com.gfredericks.repl
  "My repl utilities."
  (:refer-clojure :exclude [comment])
  (:require [cemerick.pomegranate :as pom]
            [clojure.java.classpath :as cp]
            [clojure.java.shell :as shell]
            [clojure.pprint :as pprint]
            [clojure.test :as test]
            [clojure.tools.namespace.find :as ns]
            [clojure.walk :as walk]))

;;;
;;; Dependencies
;;;

(def ^:private added-deps (atom #{}))

(defn add-dep*
  [lein-mvn-coords]
  (when-not (contains? @added-deps lein-mvn-coords)
    (pom/add-dependencies :coordinates [lein-mvn-coords]
                          :repositories
                          (assoc cemerick.pomegranate.aether/maven-central
                                 "clojars" "http://clojars.org/repo"))
    (swap! added-deps conj lein-mvn-coords)))

(defmacro add-dep
  "E.g.: (add-dep [com.gfredericks/z \"0.1.0\"])"
  [lein-mvn-coords]
  `(add-dep* '~lein-mvn-coords))

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

;;
;; Shelling out to bash
;;

(defn bash
  ([cmd]
   (bash cmd {}))
  ([cmd opts]
   (if (:in opts)
     (apply shell/sh "bash" "-c" cmd (apply concat opts))
     (apply shell/sh "bash" :in cmd (apply concat opts)))))

;;
;; def
;;

(defmacro def
  "A variant of def that makes the definition in whatever namespace
  this macro itself is a part of.

  Intended to be used with tools like lein-shorthand or dot-slash-2,
  where this macro can be aliased as ./def and then (./def x 42)
  would result in the var #'./x being created.

  Alternately, (./def foo/x 42) will define x in the foo namespace,
  unless foo is an alias for a longer namespace in which case it will
  use that."
  [var-name val]
  (let [ns-prefix (symbol (or (namespace (first &form))
                              (namespace var-name)))
        sym (symbol ns-prefix)
        actual-ns (or (get (ns-aliases *ns*) sym)
                      (try
                        (the-ns sym)
                        (catch Exception e
                          (create-ns sym))))
        v (doto (intern actual-ns (symbol (name var-name)))
            (alter-meta! merge (meta var-name)))]
    `(alter-var-root ~v (constantly ~val))))
