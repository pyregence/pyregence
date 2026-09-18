(ns pyregence.datatypes.tab
  "One forecast tab, as the nav bar is handed it.

   A projection and not the whole of what PyreCast sent: `capabilities` carries
   a forecast's parameters, filters and defaults too, and none of that is any of
   the nav bar's business. What survives here is the four things a tab is
   rendered and gated by."
  (:require [pyregence.datatypes.viewer :as viewer]))

(def ^:private psps-forecast
  "The forecast the PSPS zones are published under.

   Keyed and not labelled. `opt-label` is what a person reads and is free to be
   reworded; the key is what the rest of PyreCast already addresses this
   forecast by, so rewording the tab cannot silently open the gate."
  :psps-zonal)

(defrecord Tab [forecast label hover-text allowed-org-ids])

(defn ->tab
  "The tab PyreCast published under FORECAST, from the row it published.

   Nil allowed-orgs is not an empty allow-list: it says the tab names no
   organizations at all, which for every forecast but PSPS means everybody sees
   it."
  [forecast {:keys [opt-label hover-text allowed-orgs]}]
  (->Tab forecast opt-label hover-text allowed-orgs))

(defn psps?
  "Whether this is the PSPS tab."
  [tab]
  (= psps-forecast (:forecast tab)))

(defn restricted?
  "Whether this tab names the organizations it is for."
  [tab]
  (some? (:allowed-org-ids tab)))

(defn offered-to?
  "Whether this tab is offered to VIEWER.

   Three ways to be offered, and each is a sentence about a viewer or a tab:
   administering every organization; belonging to an organization the tab names
   outright; and, for PSPS alone, belonging to one PyreCast holds PSPS data for.

   PSPS is the exception in the last clause rather than a fourth clause beside
   it, because being unrestricted and being open to everybody are the same thing
   for every other tab and are not the same thing for this one -- it names no
   organizations on the account settings page, where the tab list is the static
   default, and must not be read there as open."
  [tab a-viewer]
  (or (viewer/administers-everything? a-viewer)
      (viewer/belongs-to-any-of? a-viewer (:allowed-org-ids tab))
      (if (psps? tab)
        (viewer/belongs-to-a-psps-backed-organization? a-viewer)
        (not (restricted? tab)))))

^:rct/test
(comment
  (require '[pyregence.datatypes.organization  :as organization]
           '[pyregence.datatypes.organizations :as organizations]
           '[pyregence.datatypes.session       :as session])

  (def ^:private nv-energy (organization/->organization "nve" "NV Energy" "cred"))

  (defn- viewing [role orgs psps-ids]
    (viewer/->viewer (session/->session role) orgs psps-ids))

  (def ^:private a-member  (viewing "organization_member" (organizations/->organizations [nv-energy]) nil))
  (def ^:private a-stranger (viewing "organization_member" organizations/none nil))
  (def ^:private an-account-manager (viewing "account_manager" organizations/none nil))

  ;; An unrestricted tab is offered to anybody; the PSPS tab never is.
  (let [weather (->tab :fire-weather {:opt-label "Weather"})
        psps    (->tab :psps-zonal   {:opt-label "PSPS"})]
    [(offered-to? weather a-stranger)
     (offered-to? psps    a-stranger)])
  ;=> [true false]

  ;; The account settings page: the tab list is the static default, so PSPS names
  ;; no organizations and is gated purely on PyreCast holding data for one of the
  ;; viewer's. This is the case a `(nil? allowed-orgs)` reading opens by mistake.
  (let [psps (->tab :psps-zonal {:opt-label "PSPS"})]
    [(offered-to? psps (viewing "organization_member"
                                (organizations/->organizations [nv-energy])
                                #{"nve"}))
     (offered-to? psps (viewing "organization_member"
                                (organizations/->organizations [nv-energy])
                                #{"someone-else"}))
     (offered-to? psps (viewing "organization_member"
                                (organizations/->organizations [nv-energy])
                                nil))])
  ;=> [true false false]

  ;; The forecast page: PSPS carries its own allow-list instead, assoc'd into
  ;; capabilities by process-capabilities!, and no psps-backed-ids are passed.
  (let [psps (->tab :psps-zonal {:opt-label "PSPS" :allowed-orgs #{"nve"}})]
    [(offered-to? psps a-member)
     (offered-to? psps a-stranger)])
  ;=> [true false]

  ;; A restricted non-PSPS tab is offered only to the organizations it names...
  (let [utility (->tab :fire-risk {:opt-label "Risk" :allowed-orgs #{"nve"}})]
    [(offered-to? utility a-member)
     (offered-to? utility a-stranger)])
  ;=> [true false]

  ;; ...and an account manager administers every organization, so sees all of it.
  (mapv #(offered-to? % an-account-manager)
        [(->tab :fire-risk  {:allowed-orgs #{"nve"}})
         (->tab :psps-zonal {})])
  ;=> [true true]

  ;; Keyed, not labelled: rewording the label cannot open the gate.
  (offered-to? (->tab :psps-zonal {:opt-label "Power Shutoffs"}) a-stranger)
  ;=> false
  )
