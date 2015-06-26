(ns com.gfredericks.repl.stashing)

(defn ^:private enqueue-max
  [q max x]
  (let [q' (conj q x)]
    (cond-> q'
      (> (count q') max)
      (pop))))

(defn ^:private all-vars
  [ns]
  (->> (ns-map ns)
       (vals)
       (filter var?)
       (filter #(= ns (.ns ^clojure.lang.Var %)))))

(def ^:private empty-stash
  {:returned clojure.lang.PersistentQueue/EMPTY
   :caught clojure.lang.PersistentQueue/EMPTY})

(defn stash-everything!
  "Stashes args, return values, and exceptions thrown for all of the
  fuctions defined in the current namespace."
  []
  (doseq [v (all-vars *ns*)
          :when (fn? @v)
          :let [tracking (atom empty-stash)]]
    (alter-meta! v assoc ::stash tracking)
    (alter-var-root v
                    (fn [orig]
                      (fn [& args]
                        (try
                          (let [ret (apply orig args)]
                            (swap! tracking update-in [:returned] enqueue-max 10
                                   {:args args :return ret :at (java.util.Date.)})
                            ret)
                          (catch Throwable t
                            (swap! tracking update-in [:caught] enqueue-max 10
                                   {:args args :ex t :at (java.util.Date.)})
                            (throw t))))))))

(defn get-stash
  [a-var]
  (let [{:keys [returned caught]} (-> a-var meta ::stash deref)]
    ;; converting to seq since queue's don't print well :/
    {:returned (seq returned)
     :caught (seq caught)}))

(defn stashed-exceptions
  "Returns all stashed exceptions in all namespaces, in the order they
  were thrown."
  []
  (->> (all-ns)
       (mapcat all-vars)
       (keep #(::stash (meta %)))
       (mapcat #(:caught (deref %)))
       (sort-by :at)
       (map :ex)
       (distinct)))

(defn clear-all-stashes!
  "Clears all stashes in all namespaces"
  []
  (doseq [ns (all-ns)
          var (all-vars ns)
          :let [stash (::stash (meta var))]
          :when stash]
    (reset! stash empty-stash)))
