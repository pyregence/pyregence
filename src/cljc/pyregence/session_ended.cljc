(ns pyregence.session-ended
  "How PyreCast says a session has ended, in the one place both sides read it.

   The server answers a refusal whose reason is the session with `status` and
   `message`; the page recognizes that refusal by exactly that pair and by
   nothing else. So the sentence is not copy -- it is the protocol -- and the two
   halves have to be the same object rather than two literals that agree.

   They were two literals, in `handlers.clj` and in `async_utils.cljs`, pinned to
   each other by an rct that slurped the ClojureScript and compared strings. That
   worked, and it said plainly that the arrangement was wrong: a test whose job
   is to notice two copies drifting is a test standing in for a definition."
  (:require [clojure.string :as str]))

(def message
  "What a person is told when the reason they were refused is that their session
   ended."
  "Your session has ended. Please log in again.")

(def status
  "401 rather than 403, because that is what actually went wrong: not \"you may
   not\" but \"I no longer know who you are\"."
  401)

(defn refusal?
  "Whether RESPONSE is PyreCast turning a caller away because their session
   ended, rather than for any of the other reasons it turns callers away."
  [response]
  (and (= status (:status response))
       (string? (:body response))
       (str/includes? (:body response) message)))

^:rct/test
(comment
  ;; Recognized by the pair, so neither half alone is enough: a 401 from
  ;; somewhere else, and this sentence carried by anything but a 401, are both
  ;; somebody else's business.
  (mapv refusal?
        [{:status 401 :body message}
         {:status 401 :body (str "{\"error\": \"" message "\"}")}
         {:status 401 :body "Unauthorized"}
         {:status 403 :body message}
         {:status 200 :body message}
         {:status 401 :body nil}
         {}])
  ;=> [true true false false false false false]
  )
