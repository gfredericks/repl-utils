(ns com.gfredericks.repl.util)

(let [sentinel (Object.)]
  (defn promise->future
    "Given a promise that will either be delivered with {:val x} or
  {:err t}, returns a future-like object that when dereferenced
  will either return x or throw t."
    [p]
    (letfn [(wait []
              (let [m @p]
                (if-let [pair (find m :val)]
                  (val pair)
                  (if-let [pair (find m :err)]
                    (throw (java.util.concurrent.ExecutionException.
                            ^Throwable (val pair)))
                    (throw (Error. "Promise in promise->future delivered with invalid value!"))))))]
      (reify
        clojure.lang.IDeref
        (deref [_] (wait))
        clojure.lang.IBlockingDeref
        (deref
          [_ timeout-ms timeout-val]
          (let [v (deref p timeout-ms sentinel)]
            (if (= sentinel v) timeout-val (wait))))
        clojure.lang.IPending
        (isRealized [_] (realized? p))
        java.util.concurrent.Future
        (get [_] (wait))
        (get [this ^long timeout ^java.util.concurrent.TimeUnit unit]
          (if (= sentinel
                 (deref this (.toMillis unit timeout)))
            (java.util.concurrent.TimeoutException.)
            (wait)))
        (isCancelled [_] false)
        (isDone [_] (realized? p))
        ;; (cancel [_ interrupt?] (.cancel fut interrupt?))
        ))))

(defn time-str
  [ms-delta]
  (let [quot-rem (juxt quot rem)
        [sec' ms] (quot-rem ms-delta 1000)
        [min' sec] (quot-rem sec' 60)
        [hour' min] (quot-rem min' 60)
        [day' hour] (quot-rem hour' 24)]
    (cond (< sec' 60)
          (format "%d.%03d seconds" sec ms)

          (< min' 60)
          (format "%d:%02d minutes" min sec)

          (< hour' 24)
          (format "%d:%02d hours" hour min)

          :else
          (format "%d days, %d hours" day' hour))))
