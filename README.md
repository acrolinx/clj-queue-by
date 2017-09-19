# Queue-by

A fair queue which schedules by key.

## Motivation

This library was developed with a program in mind that required a
central in-memory queue allowing the program to serve active users in
a timely manner while still ensuring that users with massive traffic
to get their job done.

Other options like
Clojure's [core.async](https://github.com/clojure/core.async),
`clojure.lang.PersistentQueue`, and
Java's
[java.util.PriorityQueue](https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html) were
considered but none met the requirements.

So we wrote `queue-by` which does what we need and has a nice name,
too.

## Usage

To use this library in your project, add this to your `:dependencies`
in your `project.clj` or `build.boot`:

    [com.acrolinx.clj-queue-by "0.1.0"]
    
To create a new queue, require the `com.acrolinx.clj-queue-by`
namespace and call `queue-by`:

    (ns test-the-queue.core
       (:require [com.acrolinx.clj-queue-by :as q]))
    
    ;; Can store this in an atom or as a local variable that you pass
    around. Leaving default size of 128.
    (def the-queue (q/queue-by :name))
    ;; When you try to push more items that the limit, an exception
    ;; will be thrown
    
    ;; Add an item to the queue by calling it with an argument
    (the-queue {:name "alice" :a 1})
    ;; This works, because the queue implements the IFn interface used
    ;; to call functions in Clojure
    
    ;; How many items are in the queue?
    (count the-queue)
    ;; The queue implements Counted, the interface behind the count fn
    ;; Performance guarantees are inherited from Clojure's hash map
    ;; and clojure.lang.PersistentQueue which are used under the hood. 
    
    ;; What is inside of the queue?
    (deref the-queue)
    @the-queue
    ;; This returns a two-element vector: first the current snapshot
    ;; queue (a clojure.lang.PersistentQueue),second a hash-map with
    ;; the per-user queues (again clojure.lang.PersistentQueue).
    ;; Works by implementing IDeref. 
    ;; Can be used for monitoring.
    
    ;; Finally, read an item from the queue by calling it without an
    ;; argument.
    (the-queue)
    ;; You probably want to do this on a different thread. Make sure
    ;; to catch all exceptions to keep the thread running. Loop with a
    ;; suitable sleep time in between or use other notification
    ;; mechanisms to trigger the reading.
    ;; This returns nil when no item is in the queue
    
## Nil

The queue allows you to add `nil` items but you will not be able to
distinguish at the receiving end if `nil` was on the queue or the
queue was empty.

## Comparison with core.async

* The `core.async` library is much more sophisticated and much more
  powerful. At the same time it is also harder to use. 
* `clj-queue-by` does not support transducers while `core.async` does.
* The buffers in `core.async` which back the channels are surprisingly
  intransparent. You can not look into them or log when things are
  being dropped. Also, channels do not support derefing and
  counting. Probably for good reasons, but the use-case that triggered
  the development of `clj-queue-by` required more introspection and
  transparency.
* `clj-queue-by` is probably only useful on JVM/Clojure and not on
  ClojureScript because it assumes that pushing and popping are done
  on separate threads.
* `core.async` is battle-proved and has shown that it runs well in
  production. `clj-queue-by` is just beginning to show it. 
* Channels in `core.async` are meant to be used a lot. You can easily
  create tens or more of them. In contrast, `clj-queue-by` was
  developed to be the central in-memory queue for a program.

## Scheduling

On the sending and the receiving end, the queue behaves just like any
other queue. Internally though, items are put into separate queues
given by the `key-fn` you define.

This is somewhat related to the (Completely Fair
Scheduling)[https://en.wikipedia.org/wiki/Completely_Fair_Scheduler]
algorithm used in the Linux Kernel and the (Fair
Scheduler)[https://hadoop.apache.org/docs/stable/hadoop-yarn/hadoop-yarn-site/FairScheduler.html]
used in Apache Hadoop.

Imagine having hash-map items with a `:name` key in it:

     {:name "alice"
      :data 1}

If you push several items with different names into the queue, it will
create separate queues per key.

    "alice": item1, item2
    "bob":   item3

Now, if Alice puts many items into the queue and the consumption of
the items takes a while, Bob would have to wait a long time for his
thing to happen.

That's why this queue implementation always takes off the leading
items of the queue per user and deliveres them.

1. Push `{:name "alice" :data 1}`
2. Push `{:name "alice" :data 2}`
3. Push `{:name "alice" :data 3}`
4. Push `{:name "bob"   :data "x"}`

When you now start pulling it will deliver the items in this order:

1. `{:name "alice" :data 1}`
2. `{:name "bob"   :data "x"}`
3. `{:name "alice" :data 2}`
4. `{:name "alice" :data 3}`

Note, how Bob's item got delivered before Alice's second. 

Think of it as taking a snapshot of the heads of the queue when
polling and then delivering this snapshot. When it is empty, a new
snapshot is taken.  Actually, is is exactly what happens behind the
scenes. 

Another example:

1. Push `{:name "alice" :data 1}`
2. Push `{:name "bob"   :data "x"}`
3. Pull `{:name "alice" :data 1}`. This takes a snapshot and delivers
   the oldest item. The second item from Bob now the head of the
   snapshot.
4. Push `{:name "alice" :data 2}`. This adds the new item after the
   snapshot.
5. Push `{:name "alice" :data 3}`
6. Pull `{:name "bob"   :data "x"}`
7. Pull `{:name "alice" :data 2}`
8. Pull `{:name "alice" :data 3}`

A last example:

1. Push `{:name "alice" :data 1}`
2. Push `{:name "bob"   :data "x"}`
3. Push `{:name "alice" :data 2}`
4. Pull `{:name "alice" :data 1}`. This takes a snapshot and delivers
   the oldest item. The second item from Bob now the head of the
   snapshot. The second item from Alice stays on her dedicated queue.
5. Push `{:name "alice" :data 3}`
6. Push `{:name "alice" :data 4}`
7. Pull `{:name "bob"   :data "x"}`. Was head of snapshot. 
7. Push `{:name "bob"   :data "y"}`
8. Pull `{:name "alice" :data 2}`. A new snapshot is created with the
   head items of both Alice and Bob added. Thus, Bob's item overtakes
   Alice's larger queue of items.
9. Pull `{:name "bob" :data "y"}`
9. Pull `{:name "alice" :data 3}`
10. Pull `{:name "alice" :data 4}`

## License

Copyright © 2017 Acrolinx GmbH

Distributed under the Apache Software License either version 2.0 or
(at your option) any later version.
