[![Build Status](https://travis-ci.org/acrolinx/clj-queue-by.svg?branch=master)](https://travis-ci.org/acrolinx/clj-queue-by)
[![Clojars Project](https://img.shields.io/clojars/v/com.acrolinx/clj-queue-by.svg)](https://clojars.org/com.acrolinx/clj-queue-by)

# Queue-by

A queue which schedules fairly by key.

## Motivation

We developed this library with a program in mind that requires a
central in-memory queue. The queue must allow the program to serve
active users in a timely manner while still ensuring that users with
massive traffic get their job done eventually.

We considered other options like
Clojure's [core.async](https://github.com/clojure/core.async),
`clojure.lang.PersistentQueue`, and
Java's
[java.util.PriorityQueue](https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html) but
none met the requirements.

So we wrote `queue-by` which does what we need and has a nice name,
too.

## Usage

To use this library in your project, add the following to your
`:dependencies` in your `project.clj` or `build.boot`:

    [com.acrolinx.clj-queue-by "0.1.1"]

To create a queue, `require` the `com.acrolinx.clj-queue-by` namespace
and call `queue-by`:

    (ns test-the-queue.core
       (:require [com.acrolinx.clj-queue-by :as q]))
    
    (def queue (q/queue-by :name))

Here we create the queue with a `key-fn` `:name`, so items will get a
dedicated queue per `:name`. You can store the queue in an atom,
too. Alternatively, create it as a local variable and pass it
around.

For a detailed description of the scheduling algorithm per key,
see [The Scheduling Mechanism](#the-scheduling-mechanism) below.

The queue can have a maximum size. In the first example, we stick to
the default maximum of 128:

    (def queue (q/queue-by :name))

If you want to have a different limit, call it with a second argument:

    (def queue (q/queue-by :name) 1000)

If the second argument is an explicit `nil` the queue is unbounded and
no size checks are performed. Use at your own risk.

    (def queue (q/queue-by :name) nil)

Add an item to the queue by calling it with the item as the argument:

    (queue {:name "alice" :a 1})

When you try to push more items than the limit, an exception is
thrown.

    (def queue (q/queue-by :id 1))
    (queue {:id 1})
    (try (queue {:id 2})
           (catch clojure.lang.ExceptionInfo e
             (ex-data e)))
    ;=> {:item {:id 2}, :current-size 1}

Calling the queue like a function works, because it implements the
`IFn` interface which is used when calling functions in Clojure.

How many items are in the queue?

    (count queue)

The queue implements `Counted`, the interface behind the `count`
function. Performance guarantees are inherited from Clojure's hash map
and `clojure.lang.PersistentQueue` which are used under the hood.
    
What's inside the queue?

    (deref queue)
    @queue

Dereferencing the queue returns a two-element vector: first the
current snapshot queue (a `clojure.lang.PersistentQueue`), second a
hash-map with the per-key queues (again
`clojure.lang.PersistentQueue`). Works by implementing `IDeref`. The
dereferenced information can be used for monitoring.
    
Finally, read an item from the queue by calling it without an
argument.

    (queue)

Reading from the queue returns `nil` when no item is in the queue.

## Nil

The queue allows you to add `nil` items but you won't be able to
distinguish at the receiving end if `nil` was in the queue or the
queue was empty.

Also, `nil` gets its own queue when the `key-fn` returns `nil` just as
any other value.

## Comparison with core.async

* The `core.async` library is much more sophisticated and much more
  powerful. At the same time, it is also harder to use.
* `clj-queue-by` doesn't support transducers while `core.async` does.
* The buffers in `core.async` which back the channels are surprisingly
  intransparent. You can't look into them or log when things are being
  dropped. Also, channels do not support derefing and
  counting. Probably for good reasons, but the use-case that triggered
  the development of `clj-queue-by` required more introspection and
  transparency. In `core.async`, you can overcome all these limitations
  if you implement your own buffer to back a channel. In fact, we did
  this previously.
* `core.async` is battle-proven and has shown that it runs well in
  production. `clj-queue-by` is just beginning to show it. 
* Channels in `core.async` are meant to be used a lot. You can easily
  create tens or hundreds of them. In contrast, `clj-queue-by` was
  developed to be the central in-memory queue for a program.

## The Scheduling Mechanism

On the sending and the receiving end, the queue behaves just like any
other queue. Internally though, items are put into separate queues
given by the `key-fn` you define.

The scheduling mechanism is somewhat related to
the
[Completely Fair Scheduling](https://en.wikipedia.org/wiki/Completely_Fair_Scheduler) algorithm
used in the Linux Kernel and
the
[Fair Scheduler](https://hadoop.apache.org/docs/stable/hadoop-yarn/hadoop-yarn-site/FairScheduler.html) used
in Apache Hadoop.

Imagine having hash-map items with a `:name` key in it:

     {:name "alice"
      :data 1}

If you push several items with different names into the queue, it
creates separate queues per key.

    "alice": item1, item2
    "bob":   item3

Now, if Alice puts many items into a common FIFO queue and the
consumption of the items takes a while, Bob would have to wait a long
time for his thing to happen.

To solve this problem, this queue implementation always takes off the
leading items of the queue per user and delivers them.

1. Push `{:name "alice" :data 1}`
2. Push `{:name "alice" :data 2}`
3. Push `{:name "alice" :data 3}`
4. Push `{:name "bob"   :data "x"}`

After these pushes, the queue looks like this internally:

    alice: {:name "alice" :data 1},
           {:name "alice" :data 2},
           {:name "alice" :data 3},
    bob:   {:name "bob"   :data "x"}
    
When you start pulling, it will deliver the items in this order:

1. Pull `{:name "alice" :data 1}`
2. Pull `{:name "bob"   :data "x"}`
3. Pull `{:name "alice" :data 2}`
4. Pull `{:name "alice" :data 3}`

Note, how Bob's item got delivered before Alice's second item.

Think of it as taking a snapshot of the heads of the queue when
polling and then delivering this snapshot. When it's empty, a new
snapshot is taken. Actually, this is exactly what happens behind the
scenes.

Another example:

1. Push `{:name "alice" :data 1}`
2. Push `{:name "bob"   :data "x"}`
3. Pull `{:name "alice" :data 1}`. This takes a snapshot and delivers
   the oldest item. The item from Bob is now the head of the snapshot.
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
   the oldest item. The item from Bob is now the head of the
   snapshot. Alice's second item stays on her dedicated queue.
5. Push `{:name "alice" :data 3}`
6. Push `{:name "alice" :data 4}`
7. Pull `{:name "bob"   :data "x"}`. Was head of the snapshot.
8. Push `{:name "bob"   :data "y"}`
9. Pull `{:name "alice" :data 2}`. A new snapshot is created with the
   head items of both Alice and Bob added. Thus, Bob's item overtakes
   Alice's larger queue of items.
10. Pull `{:name "bob" :data "y"}`
11. Pull `{:name "alice" :data 3}`
12. Pull `{:name "alice" :data 4}`

If we reduce the items to just the `:data`, the queue in this example
went through the following internal states (empty queues suppressed):

    1.
    alice: data 1
    
    2.
    alice: data 1
    bob:   data x
    
    3.
    alice: data 1, data 2
    bob:   data x
    
    4.
    SNAPSHOT:    bob/data x
    alice:       data 2
    -> Returned: alice/data 1

    5.
    SNAPSHOT:    bob/data x
    alice:       data 2, data 3

    6.
    SNAPSHOT:    bob/data x
    alice:       data 2, data 3, data 4

    7.
    alice:       data 2, data 3, data 4
    -> Returned: bob/data x
    
    8.
    alice:       data 2, data 3, data 4
    bob:         data y
    
    9.
    SNAPSHOT:    bob/data y
    alice:       data 3, data 4
    -> Returned: alice/data 2
    
    10.
    alice:       data 3, data 4
    -> Returned: bob/data y
    
    11.
    alice:       data 4
    -> Returned: alice/data 3

    11.
    -> Returned: alice/data 4

## Contributing

This project accepts pull request from registered authors. We ask you
to sign our Contributor License Agreement first. Please reach out to
us at https://www.acrolinx.com/contact/ so that we can prepare
everything.

Don't panic. It is just some easy legalese that helps us maintain the
availability of this software for everyone.

## Contributors

* @replrep :computer: :mag:
* @nblumoe :mag:

Type of contribution:

* :mag: Feedback, review
* :computer: Code, pull request

## License

Copyright © 2017 Acrolinx GmbH

Distributed under the Apache Software License either version 2.0 or
(at your option) any later version.
