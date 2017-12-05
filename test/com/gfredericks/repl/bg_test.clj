(ns com.gfredericks.repl.bg-test
  (:require [clojure.test :as t]
            [com.gfredericks.repl.bg :refer [bg]]))

(t/deftest string-writer-test
  (with-out-str ;; suppress printing for tests
    (let [bg (bg (Thread/sleep 0.3) 42)]
      (t/is (re-matches #"#<(DONE: bg0 ran for|bg0 has been running for).*>"
                        (pr-str bg)))
      (t/is (= 42 (-> bg meta :future deref))))))
