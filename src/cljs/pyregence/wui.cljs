(ns pyregence.wui
  "Helpers for WUI active fires: a private set of fire-spread forecasts owned by the
   pyregence-consortium organization and served from the :psps GeoServer."
  (:require [pyregence.config :as c]
            [pyregence.state  :as !]))

(def wui-org-unique-id
  "The `org-unique-id` of the organization that owns WUI active fires. Only members of
   this org may view the WUI Fire select."
  "pyregence-consortium")

(defn user-in-wui-org?
  "Returns true if the given list of user organizations includes the WUI org."
  [user-orgs-list]
  (boolean (some #(= wui-org-unique-id (:org-unique-id %)) user-orgs-list)))

(defn wui-fire-selected?
  "True when the user has selected a specific WUI active fire. WUI fires are served
   from the private :psps GeoServer and are keyed off :wui-fire-name (rather than
   :fire-name like regular active fires on :trinity)."
  []
  (and (= @!/*forecast :active-fire)
       (not= :none (get-in @!/*params [:active-fire :wui-fire-name]))))

(defn show-wui-fires?
  "True when WUI active fires should be surfaced into the active-fire params: the
   :wui feature flag is on, the user belongs to the WUI org, and there is at least
   one WUI fire available."
  [user-orgs-list wui-active-fires]
  (and (c/feature-enabled? :wui)
       (user-in-wui-org? user-orgs-list)
       (seq wui-active-fires)))
