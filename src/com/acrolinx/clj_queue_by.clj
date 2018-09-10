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

(ns com.acrolinx.clj-queue-by)

;; Arbitrary default size
(def ^:private ^:const DEFAULT-QUEUE-SIZE 128)

;;; Persistent Queue Helpers

(defmethod print-method clojure.lang.PersistentQueue
  [q ^java.io.Writer w]
  (.write w "#<<")
  (print-method (sequence q) w))

(defn- persistent-empty-queue []
  clojure.lang.PersistentQueue/EMPTY)

(defn- persistent-queue
  "Create a persistent queue, optionally with init values.

  If an init value is provided it is added to the queue. For a
  sequential init value, all items are added to the queue separately."
  ([] (persistent-empty-queue))
  ([x]
   (if (sequential? x)
     (reduce conj (persistent-empty-queue) x)
     (conj (persistent-empty-queue) x))))

(defn- quonj
  "Conj x on queue, ensuring q is a persistent queue.

  If q is nil, it is created as a clojure.lang.PersistentQueue and x
  is conj'ed onto it. Otherwise, if q is defined x is just conj'ed.

  Don't waste time checking for correct type as this is an internal fn
  and we know what to expect."
  [q x]
  (if q (conj q x)
      (persistent-queue x)))

;; This particular queue implementation

(defn- internal-queue
  "Returns internal representation of the queue.

  The first item of this vector is the current snapshot of already
  selected items. Its items are returned on pop until the selected
  queue is empty. Then a new snapshot is taken from the queued items
  in the map which is the second item of this vector. The keys of the
  map are created with the key-fn which is passed to the
  constructor."
  []
  [(persistent-empty-queue) {}])

(defn- queue-count
  "Given a derefed queue, returns a count of all items.

  Sum of items already selected plus all items in the separate
  queues."
  [[selected queued]]
  (reduce (fn [sum [k countable-val]]
            (+ sum (count countable-val)))
          (count selected)
          queued))

(defn- persistent-data-queue
  "Extracts the ::data field from all items in sequence s into a
  PersistentQueue."
  [s]
  (persistent-queue (map ::data s)))

(defn- queue-deref [the-q]
  (let [[selected queued] @the-q]
    [(persistent-data-queue selected)
     (reduce
      (fn [acc [k pq]]
        (assoc acc k (persistent-data-queue pq)))
      {}
      queued)]))

(defn- queue-push
  "Backend function to perform the push to the queue.

  Throws exception when MAX-SIZE is non-nil and reached.
  Alters internal queue and index state by side-effect."
  [the-q the-index keyfn max-size it]
  
  (dosync
   (when max-size
     (let [cnt (queue-count @the-q)]
       (when (>= cnt max-size)
         (throw (ex-info "Queue overflow."
                         {:item it
                          :current-size cnt})))))
   (alter the-index inc)
   (alter the-q update-in [1 (keyfn it)] quonj
          ;; uses in-transaction value already inced
          {::data it
           ::id   @the-index})))

(defn- pop-from-selected [the-q]
  (let [[selected queued] @the-q
        head (peek selected)
        tail (pop selected)]
    (alter the-q assoc 0 tail)
    head))

(defn- peeks-and-pops
  "Returns the snapshot and remainder of the queues in QUEUE-MAP.

  This iterates over all the queues in QUEUE-MAP and computes the head
  and tails of each. The heads become a new persistent queue, ordered
  by time of arrival in the queue. The tails become the new
  sub-queues."
  [queue-map]
  (let [[heads tails]
        (reduce-kv (fn [[head-acc tail-acc] k queue]
                     [(conj head-acc (peek queue))
                      (assoc tail-acc k (pop queue))])
                   [[] {}]
                   queue-map)]
    [(persistent-queue (sort-by ::id heads))
     (into {} (filter (fn [[k queue]] (not-empty queue)) tails))]))

(defn- select-snapshot! [the-q]
  (let [[heads tails] (peeks-and-pops (second @the-q))]
    (alter the-q assoc 0 heads)
    (alter the-q assoc 1 tails)))

(defn- queue-pop
  "Pops an item from the queue.

  This is where the hard work is done.  Need to transparently take a
  snapshot of all leading items in all current queues. Then remove
  those items from the internal queues."
  [the-q]
  (dosync
   (let [[selected queued] @the-q
         selected-size (count selected)]
     (when (= 0 selected-size)
       (select-snapshot! the-q))
     (::data (pop-from-selected the-q)))))

(deftype QueueByQueue [the-q the-index keyfn max-q-size]
  clojure.lang.Counted
  (count [this]
    (queue-count @the-q))

  clojure.lang.IDeref
  (deref [this] (queue-deref the-q))

  clojure.lang.IFn
  ;; zero args: read a value
  (invoke [this]
    (queue-pop the-q))
  ;; one arg: add the value
  (invoke [this it]
    (queue-push the-q the-index keyfn max-q-size it)
    this))

(defn queue-by
  ([keyfn]
   (queue-by keyfn DEFAULT-QUEUE-SIZE))
  ([keyfn max-q-size]
   (let [the-q (ref (internal-queue))
         the-index (ref 0)]
     (QueueByQueue. the-q
                    the-index
                    keyfn
                    max-q-size))))
