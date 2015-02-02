(ns com.gfredericks.repl
  "My repl utilities."
  (:require [cemerick.pomegranate :as pom]
            [clojure.pprint :as pprint]
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
  "Like clojure.repl/dir but also works with local aliases."
  [ns-name-or-alias]
  (list `clojure.repl/dir
        (if-let [ns (get (ns-aliases *ns*) ns-name-or-alias)]
          (.getName ns)
          ns-name-or-alias)))


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
