(ns compassus.runner
  (:require [cljs.test :refer-macros [run-tests]]
            [cljs.nodejs]
            [compassus.tests]))

(enable-console-print!)

(defn main []
  (run-tests 'compassus.tests))

(set! *main-cli-fn* main)
