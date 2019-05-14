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
           #?(:clj clojure.lang.ExceptionInfo
              :cljs cljs.core.ExceptionInfo)
           #"overflow"
           (q {:x 1})))
      (is (= 2 (count q)))))
  (let [q (tt/queue-by :x)]
    (testing "Adding more values than allowed (default size)"
      (dotimes [i @#'com.acrolinx.clj-queue-by/DEFAULT-QUEUE-SIZE] (q {:x 2}))
      (is (thrown-with-msg?
           #?(:clj clojure.lang.ExceptionInfo
              :cljs cljs.core.ExceptionInfo)
           #"overflow"
           (q {:x 1})))))
  (let [q (tt/queue-by :x nil)]
    (testing "Adding more values than allowed per default to unbounded queue"
      (dotimes [i @#'com.acrolinx.clj-queue-by/DEFAULT-QUEUE-SIZE] (q {:x 2}))
      (q {:x 1}))))

(deftest deref-test
  (let [qb (tt/queue-by :i)
        pq (#'com.acrolinx.clj-queue-by/persistent-empty-queue)]

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

