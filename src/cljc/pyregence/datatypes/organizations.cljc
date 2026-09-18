(ns pyregence.datatypes.organizations
  "The organizations a session has been told about, answering as a whole rather
   than handing its collection over."
  (:refer-clojure :exclude [any?]))

(defrecord Organizations [all])

(defn ->organizations
  "The organizations in ALL, in the order PyreCast gave them."
  [all]
  (->Organizations (vec all)))

(def none
  "Belonging to nothing. Distinct from nil, which is not having been told."
  (->organizations []))

(defn any?
  "Whether this session belongs to any organization at all."
  [organizations]
  (boolean (seq (:all organizations))))

(defn backed-by
  "Those of these organizations PyreCast holds PSPS data for, given the IDS it
   said it holds data for.

   Nil ids -- not having been told -- back nothing, and so do empty ones. Held
   here rather than guarded at each call site: what an absent id list means is
   this namespace's business, and a caller writing `(or ids #{})` in front of the
   question is a caller reasoning about a shape instead of asking one."
  [organizations ids]
  (->organizations (filter (comp (set ids) :id) (:all organizations))))

(defn allowed?
  "Whether any of these organizations is among ALLOWED-IDS.

   Nil allowed-ids allows nobody, which is not the same claim as a tab that names
   no organizations at all -- that one is open to everyone, and the caller asking
   it must say so itself rather than read it out of this answer."
  [organizations allowed-ids]
  (boolean (some (comp (set allowed-ids) :id) (:all organizations))))

(defn a-credential
  "The credential of whichever of these organizations lends one; nil when none
   does. Whichever, deliberately: the layers this answers for are shared across
   every utility, so which one paid for the credential does not matter."
  [organizations]
  (some :credential (:all organizations)))

(defn all
  "These organizations, one at a time, for a caller projecting them into
   something this namespace has no business knowing about -- an option row on a
   panel, a default selection. Every question with a domain answer is a function
   above; reach for this only when the answer is a shape."
  [organizations]
  (:all organizations))

(defn credential-for
  "The credential lent for the organization known by ID; nil where none was, and
   nil where the organization is not one of these. Asked of a session's own
   organizations rather than of all of them: a credential this session may not
   use is one PyreCast should never hand over."
  [organizations id]
  (some #(when (= id (:id %)) (:credential %)) (:all organizations)))
