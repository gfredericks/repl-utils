(ns com.gfredericks.repl-test
  (:require [clojure.test :as t]
            [com.gfredericks.repl :as repl]))

(t/deftest def-test
  (repl/def x 42)
  (t/is (= 42 repl/x)))
