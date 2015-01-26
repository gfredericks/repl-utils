(ns com.gfredericks.repl
  "My repl utilities."
  (:require [cemerick.pomegranate :as pom]
            [clojure.pprint :as pprint]
            [clojure.walk :as walk]
            [clojure.tools.analyzer.passes.emit-form :as e]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.analyzer.jvm :as ctajvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :as j.e]))

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
