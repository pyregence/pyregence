(ns pyregence.wiring
  "Which concretion answers each concept, and the one place a run says so.

   One file and not two. Splitting the protocol into `api/` and the ambient into
   `composition/` puts the arrow the wrong way round -- an archetype sits below
   composition and would have to import upward to reach it -- and leaves a
   reader two addresses for one decision."
  #?@(:cljs [(:require [pyregence.archetypes.directory.over-the-wire :as arch.directory.over-the-wire]
                       [pyregence.archetypes.session-activity.local-storage :as arch.session-activity.local-storage])
             (:require-macros [pyregence.wiring])]))

(defprotocol IWiring
  "Every row *is* a constructor: it builds this realization's concept and
   answers with it. A row answering with a constructor for the caller to invoke
   was tried and is worse -- every call site reads `((-directory w) a-session)`,
   and the arity a row expects is written down nowhere.

   Each row's arguments after the wiring are the concept's dependency set."
  (-directory [wiring a-session]
    "The directory answering for A-SESSION.")

  (-session-activity [wiring]
    "The activity shared by every tab in this browser session."))

#?(:cljs
   ;; No fields: nothing is assembled here, so there is nothing to hold. Which
   ;; concretion, and that is all.
   (defrecord BrowserWiring []
     IWiring
     (-directory [_ a-session]
       (arch.directory.over-the-wire/Directory a-session))

     (-session-activity [_]
       (arch.session-activity.local-storage/SessionActivity))))

(def ^:dynamic *wiring*
  "Nil outside a composition, deliberately: an archetype asked without one
   installed should say so rather than guess."
  nil)

(defn current
  "The wiring in force."
  []
  (or *wiring*
      (throw (ex-info (str "No wiring is installed. An archetype was asked for "
                           "a concretion outside any composition -- the caller "
                           "is above a composition root, or there is a second "
                           "one.")
                      {:ambient '*wiring*}))))

(defmacro with-wiring
  "Install WIRING for the duration of BODY. Named by a composition root only;
   anywhere else is a second composition root."
  [wiring & body]
  `(binding [*wiring* ~wiring] ~@body))
