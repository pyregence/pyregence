(ns pyregence.datatypes.tile-tally
  "What the tiles each map source asked for came back as, and which sources
   that adds up to having been refused outright.

   A source is refused when not one of its tiles drew and at least one was
   turned away. One tile drawing is enough to clear it: the map is showing
   something, and a partial failure is the network's business, not a thing to
   interrupt somebody about.")

(defrecord TileTally [loaded refused reported])

(defn ->tile-tally
  "Nothing asked for yet."
  []
  (->TileTally {} {} #{}))

(defn refusal?
  "Whether a tile failing with HTTP STATUS means the source turned it away.

   Not a 400. GeoWebCache answers a tile outside a layer's bounds with one, so a
   healthy layer collects them from every pan to its edge. A failure with no
   status at all is a refusal: that is what a browser reports when the answer
   came back without the CORS headers to read it by, which is how a refused
   login to GeoServer arrives."
  [status]
  (not= 400 status))

(defn tile-loaded
  "A tile of SOURCE drew."
  [tally source]
  (update-in tally [:loaded source] (fnil inc 0)))

(defn tile-failed
  "A tile of SOURCE failed with HTTP STATUS, nil when none could be read."
  [tally source status]
  (cond-> tally
    (refusal? status) (update-in [:refused source] (fnil inc 0))))

(defn newly-refused
  "The sources refused outright that have not already been reported."
  [{:keys [loaded refused reported]}]
  (->> (keys refused)
       (remove #(contains? loaded %))
       (remove reported)
       (set)))

(defn reported
  "SOURCES have been said to be refused, and need not be again."
  [tally sources]
  (update tally :reported into sources))

^:rct/test
(comment
  ;; Refused, and said once.
  (let [t (-> (->tile-tally)
              (tile-failed "kbdi" 401)
              (tile-failed "kbdi" nil))]
    [(newly-refused t)
     (newly-refused (reported t (newly-refused t)))])
  ;=> [#{"kbdi"} #{}]

  ;; A tile off the edge of a layer is not a refusal.
  (-> (->tile-tally) (tile-failed "kbdi" 400) newly-refused)
  ;=> #{}

  ;; One tile drawing clears the source, whichever order they arrived in.
  (mapv newly-refused
        [(-> (->tile-tally) (tile-failed "kbdi" 401) (tile-loaded "kbdi"))
         (-> (->tile-tally) (tile-loaded "kbdi") (tile-failed "kbdi" 401))])
  ;=> [#{} #{}]

  ;; Sources are judged apart.
  (-> (->tile-tally)
      (tile-loaded "basemap")
      (tile-failed "kbdi" 403)
      newly-refused)
  ;=> #{"kbdi"}
  )
