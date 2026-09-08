(ns pyregence.throttle
  "Failed-attempt budgets, keyed by what was tried: [:password normalized-email] or [:2fa user-id].
   One map so the sweep has one thing to clear, two keys so a fumbled password cannot spend the
   budget the second factor needs.")

(defonce attempts (atom {}))

(def ^:private max-attempts 6)

(defn spend!
  "Charges one attempt against `k` and returns the running total. The charge is the read, so
   concurrent callers cannot all see the same under-budget count."
  [k]
  (get (swap! attempts update k (fnil inc 0)) k))

(defn over-budget?
  "Whether `total`, as `spend!` returned it, is past the allowance."
  [total]
  (< max-attempts total))

(defn forgive!
  "Forgets the counts for `ks`."
  [& ks]
  (apply swap! attempts dissoc ks))

(defn sweep!
  "Clears every count on a five-minute cycle, so a lockout is temporary. Loops in the caller's
   thread: a future in here would hand the worker an already-finished one to cancel."
  []
  (loop []
    (Thread/sleep (* 1000 60 5))
    (reset! attempts {})
    (recur)))

^:rct/test
(comment
  ;; `attempts` is a defonce and `bb test` is one JVM, so start from a known map rather than from
  ;; whatever the namespaces sorted before this one left behind.
  (reset! attempts {})

  ;; Six through, the rest over.
  (mapv (comp over-budget? spend!) (repeat 8 [:2fa 24]))
  ;=> [false false false false false false true true]

  ;; The two steps are keyed apart, so forgiving one leaves the other alone.
  (do (reset! attempts {})
      (dotimes [_ 5] (spend! [:password "a@b.c"]))
      (spend! [:2fa 24])
      (forgive! [:password "a@b.c"])
      @attempts)
  ;=> {[:2fa 24] 1}

  ;; 200 callers at once still spend six, because the charge is the read.
  (do
    (reset! attempts {})
    (let [latch   (java.util.concurrent.CountDownLatch. 1)
          results (atom [])
          threads (doall (repeatedly 200 #(Thread. ^Runnable
                                                   (fn []
                                                     (.await latch)
                                                     (swap! results conj (over-budget? (spend! [:2fa 24])))))))]
      (doseq [^Thread t threads] (.start t))
      (.countDown latch)
      (doseq [^Thread t threads] (.join t))
      (count (remove true? @results))))
  ;=> 6

  (reset! attempts {})
  )
