(ns .
  (:require [clojure.repl]
            [com.gfredericks.repl]
            [potemkin :refer [import-vars]]))

(import-vars
 [clojure.repl apropos doc pst source]
 [com.gfredericks.repl add-dep dir pprint pp bg])
