(ns pyregence.archetypes.directory.over-the-wire
  "PyreCast answering over /clj/*.

   Everything this namespace knows that nothing above it may: that there are two
   routes for one question, that which of them applies depends on the caller's
   role, that the answers come back as EDN in a string, and that a refusal is a
   false `:success`.

   Which route follows from `session/administers-every-organization?`, and the
   mapping from that question to these two route names is the only part of it
   this namespace owns. The question itself belongs to the session and is
   unit-tested there, which is the half of this namespace a clj test can
   reach."
  (:require [clojure.core.async                :refer [go <!]]
            [clojure.edn                       :as edn]
            [pyregence.api.directory           :as directory]
            [pyregence.datatypes.organization  :as organization]
            [pyregence.datatypes.organizations :as organizations]
            [pyregence.datatypes.session       :as session]
            [pyregence.utils.async-utils       :as u-async]
            [reagent.core                      :as r]))

(defn- ask
  "Call ROUTE and read its EDN body, or nil when PyreCast would not answer.

   nil on a false `:success` rather than an empty collection, because those are
   different claims above this namespace and PYR1-1623 is what happens when they
   are conflated. `call-clj-async!` has already taken the 401 path by the time
   this reads the result."
  [route]
  (go
    (let [{:keys [success body]} (<! (u-async/call-clj-async! route))]
      (when success
        (edn/read-string body)))))

(defn- reported
  "The wire's rows as the value the rest of the page speaks. The three fields
   named here are the whole of what a page is entitled to; the two routes carry
   seven in common and diverge on five more, none of which anything reads."
  [rows]
  (organizations/->organizations
   (map (fn [{:keys [org-unique-id org-name geoserver-credentials]}]
          (organization/->organization org-unique-id org-name geoserver-credentials))
        rows)))

(defrecord PyreCastDirectory [a-session !reported !psps-reported]
  directory/IDirectory
  (refresh! [_]
    ;; The go block's own channel is the completion signal.
    (go
      ;; Two routes, one question. `get-all-organizations` hands an account
      ;; manager every organization -- they administer all of them, so all of
      ;; them are theirs.
      (when-let [rows (<! (ask (if (session/administers-every-organization? a-session)
                                 "get-all-organizations"
                                 "get-current-user-organization")))]
        (reset! !reported (reported rows)))
      ;; A bare vector of org_unique_id strings, gated on the auth token only and
      ;; not on a session. It says which organizations PSPS data exists for and
      ;; nothing about who may see it, so it survives a session that does not.
      (when-let [ids (<! (ask "get-psps-organizations"))]
        (reset! !psps-reported (set ids)))))

  (organizations [_]
    @!reported)

  (psps-backed-ids [_]
    @!psps-reported))

(defn Directory
  "Named for the concept, as every realization's one exported constructor is.

   Reagent atoms, so a component that read the directory before PyreCast
   answered re-renders when it does. That is what lets `refresh!` return
   nothing: nobody has to sequence on it."
  [a-session]
  (->PyreCastDirectory a-session (r/atom nil) (r/atom nil)))
