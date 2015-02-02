(ns com.gfredericks.repl.mexpand
  "Decent recursive macroexpansion, thanks to Nicola Mometto."
  (:require [clojure.tools.analyzer.passes.emit-form :as e]
            [clojure.tools.analyzer.env :as env]
            [clojure.tools.analyzer.jvm :as ctajvm]
            [clojure.tools.analyzer.passes.jvm.emit-form :as j.e]))

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
