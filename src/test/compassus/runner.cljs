(ns compassus.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [compassus.tests]))

(enable-console-print!)

(doo-tests 'compassus.tests)
