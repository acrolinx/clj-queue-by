(ns com.acrolinx.cljs-test-runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [com.acrolinx.clj-queue-by-test]))

(doo-tests 'com.acrolinx.clj-queue-by-test)
