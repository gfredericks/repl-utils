(ns .
  (:require [clojure.repl]
            [com.gfredericks.debug-repl]
            [com.gfredericks.repl]
            [potemkin :refer [import-vars]]))

(import-vars
 [clojure.repl apropos doc pst source]
 [com.gfredericks.debug-repl break! unbreak!]
 [com.gfredericks.repl add-dep dir pprint pp bg])
