;; Copyright 2017-2018 Acrolinx GmbH

;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
;; implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
(ns com.acrolinx.clj-queue-by-stress-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.acrolinx.clj-queue-by :as tt]))

;; FIXME: I'd feel better to have some real-world multi-threaded test
;; here and run it a 1000 times or so.
(deftest queue-multi-threaded-basic-test
   (let [q (tt/queue-by :core)]

     (testing "Adding many and get them back as expected"
       (future (q {:core :alice :a :1})
               (q {:core :bob :a :1})
               (q {:core :alice :a :2})
               (q {:core :alice :a :3})
               (q {:core :bob :a :2})
               (q {:core :charlie :a :1})
               (q {:core :charlie :a :2})
               (q {:core :charlie :a :3})
               (q {:core :charlie :a :4}))

       ;; wait for the future to start
       (Thread/sleep 100)
       ;; all blocking on the deref of the future
       (is (= {:core :alice :a :1} @(future (q))))
       (is (= {:core :bob :a :1} @(future (q))))
       (is (= {:core :charlie :a :1} @(future (q))))
       (is (= {:core :alice :a :2} @(future (q))))
       (is (= {:core :bob :a :2} @(future (q))))
       (is (= {:core :charlie :a :2} @(future (q))))
       (is (= {:core :alice :a :3} @(future (q))))
       (is (= {:core :charlie :a :3} @(future (q))))
       (is (= {:core :charlie :a :4} @(future (q))))
       (is (= 0 (count q))))))

(defn stress-reader [q max-reads]
  (let [cnt (atom 0)
        succ (atom {"t1" 0
                    "t2" 0
                    "t3" 0})]
    (loop [it nil]
      (swap! cnt inc)
      (cond

        (< max-reads @cnt)
        @succ

        (not (nil? it))
        (do
          (swap! succ update-in [(:name it)] inc)
          ;; (printf "<%s" (:id it))
          (recur (q)))

        :else
        (do
          ;; (Thread/sleep 1)
          (recur (q)))))))

(defn stress-writer [q name n]
  (dotimes [i n]
    (q {:name name :id i})
    ;; (printf ">%s" i)
    ))

(defn stress-main []
  (let [q (tt/queue-by :name 2800)
        r (future (stress-reader q 9000))
        w1 (future (stress-writer q "t1" 900))
        w2 (future (stress-writer q "t2" 900))
        w3 (future (stress-writer q "t3" 900))]
    (= @r {"t1" 900, "t2" 900, "t3" 900})))

(deftest stress-test
  (is (stress-main)))
