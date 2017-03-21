(ns com.gfredericks.repl.mexpand-test
  (:require [clojure.test :refer :all]
            [com.gfredericks.repl.mexpand :refer [mexpand-all]]))

(deftest mexpand-all-test
  (are [in out] (= out (mexpand-all in))
    '(let [x 12] (:foo x)) '(let* [x 12] (:foo x))

    '(let [a nil b 1 c "two" d 3M e :e]
       (-> a b (c d) e))
    '(let* [a nil b 1 c "two" d 3M e :e]
       (e (c (b a) d)))))
