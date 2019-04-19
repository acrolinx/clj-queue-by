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
(ns com.acrolinx.clj-queue-by-test
  (:require [clojure.test :refer [deftest testing is]]
            [com.acrolinx.clj-queue-by :as tt]))


(deftest quonj-test
  (let [t #'tt/quonj]
    (testing "Adding to empty"
      (let [it (t nil :x)]
        (is (= #?(:clj clojure.lang.PersistentQueue
                  :cljs cljs.core.PersistentQueue)
               (type it)))
        (is (= [:x] it))))
    (testing "Adding repeatedly"
      (let [it1 (t nil :x)
            it2 (t it1 :y)]
        (is (= #?(:clj clojure.lang.PersistentQueue
                  :cljs cljs.core.PersistentQueue)
             (type it2)))
        (is (= [:x :y] it2))))))


(deftest queue-sizes-test
  (let [q (tt/queue-by :key)]
    (testing "Empty"
      (is (= 0 (count q))))
    (testing "Adding one item"
      (q {:key 1 :val :ignored})
      (is (= 1 (count q))))
    (testing "Adding second item with same key"
      (q {:key 1 :val :ignored2})
      (is (= 2 (count q))))
    (testing "Adding item with new key"
      (q {:key 2 :val :ignored3})
      (is (= 3 (count q))))
    (testing "Adding second item with new key"
      (q {:key 2 :val :ignored4})
      (is (= 4 (count q))))
    (testing "Removing one item"
      (q)
      (is (= 3 (count q))))
    (testing "Removing the second item"
      (q)
      (is (= 2 (count q))))
    (testing "Removing the third item"
      (q)
      (is (= 1 (count q))))
    (testing "Removing the last item"
      (q)
      (is (= 0 (count q))))
    (testing "Removing from empty keeps the queue empty"
      (q)
      (is (= 0 (count q))))))

(deftest queue-core-principles-test
  (let [q (tt/queue-by :core)]
    (testing "Adding one, get it back"
      (q {:core :alice :a :1})
      (is (= {:core :alice :a :1} (q))))
    (testing "Adding two, get it back"
      (q {:core :alice :a :1})
      (q {:core :alice :a :2})
      (is (= {:core :alice :a :1} (q)))
      (is (= {:core :alice :a :2} (q))))
    (testing "Adding two with different keys, get it back"
      ;; note that the order is guaranteed.
      (q {:core :alice :a :1})
      (q {:core :bob   :a :1})
      (is (= {:core :alice :a :1} (q)))
      (is (= {:core :bob :a :1} (q))))
    (testing "Adding many and get them back as expected"
      (q {:core :alice   :a :1})
      (q {:core :bob     :a :1})
      (q {:core :alice   :a :2})
      (q {:core :alice   :a :3})
      (q {:core :bob     :a :2})
      (q {:core :charlie :a :1})
      (q {:core :charlie :a :2})
      (q {:core :charlie :a :3})
      (q {:core :charlie :a :4})

      ;; first snapshot
      (is (= {:core :alice   :a :1} (q)))
      (is (= {:core :bob     :a :1} (q)))
      (is (= {:core :charlie :a :1} (q)))
      ;; second
      (is (= {:core :alice   :a :2} (q)))
      (is (= {:core :bob     :a :2} (q)))
      (is (= {:core :charlie :a :2} (q)))
      ;; and so on
      (is (= {:core :alice   :a :3} (q)))
      (is (= {:core :charlie :a :3} (q)))
      (is (= {:core :charlie :a :4} (q)))
      (is (= 0 (count q))))))

;; FIXME: I'd feel better to have some real-world multi-threaded test
;; here and run it a 1000 times or so.
(deftest queue-multi-threaded-basic-test
  (let [q (tt/queue-by :core)]

    (testing "Adding many and get them back as expected"
      (future (q {:core :alice   :a :1})
              (q {:core :bob     :a :1})
              (q {:core :alice   :a :2})
              (q {:core :alice   :a :3})
              (q {:core :bob     :a :2})
              (q {:core :charlie :a :1})
              (q {:core :charlie :a :2})
              (q {:core :charlie :a :3})
              (q {:core :charlie :a :4}))

      ;; wait for the future to start
      (Thread/sleep 100)
      ;; all blocking on the deref of the future
      (is (= {:core :alice   :a :1} @(future (q))))
      (is (= {:core :bob     :a :1} @(future (q))))
      (is (= {:core :charlie :a :1} @(future (q))))
      (is (= {:core :alice   :a :2} @(future (q))))
      (is (= {:core :bob     :a :2} @(future (q))))
      (is (= {:core :charlie :a :2} @(future (q))))
      (is (= {:core :alice   :a :3} @(future (q))))
      (is (= {:core :charlie :a :3} @(future (q))))
      (is (= {:core :charlie :a :4} @(future (q))))
      (is (= 0 (count q))))))

(deftest overflow-test
  (let [q (tt/queue-by :x 2)]
    (testing "No item added"
      (is (= 0 (count q))))
    (testing "Allowed number of items added"
      (q {:x 1})
      (q {:x 1})
      (is (= 2 (count q))))
    (testing "Adding more values than allowed"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"overflow"
           (q {:x 1})))
      (is (= 2 (count q)))))
  (let [q (tt/queue-by :x)]
    (testing "Adding more values than allowed (default size)"
      (dotimes [i @#'com.acrolinx.clj-queue-by/DEFAULT-QUEUE-SIZE] (q {:x 2}))
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"overflow"
           (q {:x 1})))))
  (let [q (tt/queue-by :x nil)]
    (testing "Adding more values than allowed per default to unbounded queue"
      (dotimes [i @#'com.acrolinx.clj-queue-by/DEFAULT-QUEUE-SIZE] (q {:x 2}))
      (q {:x 1}))))

(deftest deref-test
  (let [qb (tt/queue-by :i)
        pq (clojure.lang.PersistentQueue/EMPTY)]

    (testing "Empty queue"
      (is (= [pq {}]
             @qb)))

    (testing "Add one item"
      (qb {:i "one" :x 1})
      (is (= [pq {"one" (conj pq {:i "one" :x 1})}]
             @qb)))

    (testing "Add another item, same key"
      (qb {:i "one" :x 2})
      (is (= [pq {"one" (-> pq
                            (conj {:i "one" :x 1})
                            (conj {:i "one" :x 2}))}]
             @qb)))

    (testing "Add three items, different keys"
      (qb {:i "two" :x 2})
      (is (= [pq {"one" (-> pq
                            (conj {:i "one" :x 1})
                            (conj {:i "one" :x 2}))
                  "two" (conj pq {:i "two" :x 2})}]
             @qb)))

    (testing "Three item, different keys, after snapshot"
      (is
       ;; read one item
       (= {:i "one" :x 1}
          (qb))
       ;; rest remainder
       (= [(conj pq {:i "two" :x 1})
           {"one" (conj pq {:i "one" :x 2})
            "two" pq}]
          @qb)))))

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
