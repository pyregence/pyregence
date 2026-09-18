(ns pyregence.datatypes.viewer
  "Who is looking, as far as what they are offered is concerned.

   Three things PyreCast said, held together because no question about what to
   offer can be answered from fewer than two of them. Assembled once where a
   page has all three, rather than carried around as three loose props and
   recombined at each place that asks -- which is what it was, and which put a
   role string, a collection and a set of ids into the same expression as the
   questions they were there to answer."
  (:require [pyregence.datatypes.organizations :as organizations]
            [pyregence.datatypes.session       :as session]))

(defrecord Viewer [a-session organizations psps-backed-ids])

(defn ->viewer
  "The viewer holding A-SESSION, belonging to ORGANIZATIONS, where PyreCast
   holds PSPS data for PSPS-BACKED-IDS.

   Nil is allowed for either of the last two and means PyreCast has not said.
   Not having been told is not the same as having been told nothing, and every
   question below answers no on nil rather than guessing -- offering something
   PyreCast would then refuse is the PYR1-1623 shape of bug."
  [a-session organizations psps-backed-ids]
  (->Viewer a-session organizations psps-backed-ids))

(defn administers-everything?
  "Whether every organization is one of this viewer's, and so is everything
   gated on belonging to one."
  [viewer]
  (session/administers-every-organization? (:a-session viewer)))

(defn belongs-to-a-psps-backed-organization?
  "Whether PyreCast holds PSPS data for any organization this viewer belongs to.
   The whole of what gates the PSPS zones."
  [viewer]
  (-> (:organizations viewer)
      (organizations/backed-by (:psps-backed-ids viewer))
      (organizations/any?)))

(defn belongs-to-any-of?
  "Whether this viewer belongs to any organization among IDS."
  [viewer ids]
  (organizations/allowed? (:organizations viewer) ids))
