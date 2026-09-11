(ns pyregence.datatypes.organization
  "One organization, as PyreCast reports it to a session.")

(defrecord Organization [id name credential])

(defn ->organization
  "An organization known by ID, called NAME, whose private layers are fetched
   with CREDENTIAL. Nil credential where PyreCast lent none -- not every
   organization owns private layers, and a session that may not use one is told
   nothing."
  [id name credential]
  (->Organization id name credential))
