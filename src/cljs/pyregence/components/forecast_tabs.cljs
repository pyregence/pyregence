(ns pyregence.components.forecast-tabs
  (:require
   [herb.core                   :refer [<class]]
   [pyregence.components.common :refer [tool-tip-wrapper]]
   [pyregence.styles             :as $]))

;; This is needed so that we can show the tabs on the src/cljs/pyregence/pages/account_settings.cljs page
;; The duplication of code here with `config.cljs` is not ideal
(def default-forecast-tabs
  {:fuels        {:opt-label  "Fuels"
                  :hover-text "Layers related to fuel and potential fire behavior."}
   :fire-weather {:opt-label  "Weather"
                  :hover-text "Gridded weather forecasts from several US operational weather models including key parameters that affect wildfire behavior."}
   :fire-risk    {:opt-label  "Risk"
                  :hover-text "5-day forecast of fire consequence maps. Every day over 500 million hypothetical fires are ignited across California to evaluate potential fire risk.\n"}
   :active-fire  {:opt-label  "Active Fire"
                  :hover-text "14-day forecasts of active fires with burning areas established from satellite-based heat detection."}})
   ; TODO we need a mechanism to selectively show the PSPS tab when on the /account-settings page
   ; :psps-zonal   {:opt-label  "PSPS"
   ;                :hover-text "Public Safety Power Shutoffs (PSPS) zonal statistics."}})

(defn- $forecast-label [selected? mobile?]
  {:background-color (when selected? ($/color-picker :primary-main-orange))
   :color            ($/color-picker :black)
   :cursor           "pointer"
   :font-family      "Roboto"
   :font-weight      400
   :font-size        (if mobile?
                       "12px"
                       "16px")
   :padding          (if mobile?
                       "16px 12px"
                       "16px 24px")})

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts.
   Allows super_admins to see all tabs."
  [{:keys [capabilities current-forecast on-forecast-select user-orgs-list user-role mobile?]}]
  (let [tabs (or capabilities default-forecast-tabs)]
   [:div {:style {:display "flex" :padding ".25rem 0"}}
    (doall
     (map (fn [[forecast-key {:keys [allowed-orgs hover-text opt-label]}]]
            (when (or (= user-role "super_admin")                ; Show everything for super_admins
                      (nil? allowed-orgs)                        ; Tab isn't organization-specific, so show it
                      (some (fn [{org-unique-id :org-unique-id}] ; If tab **is** organization-specific
                              (allowed-orgs org-unique-id))      ; the user must be an organization_admin or organization_member of one of the allowed orgs
                            user-orgs-list))
              ^{:key forecast-key}
              [tool-tip-wrapper
               hover-text
               :top
               [:label {:style    ($forecast-label (= current-forecast forecast-key) mobile?)
                        :class    (<class $/p-add-hover)
                        :on-click #(on-forecast-select forecast-key)}
                opt-label]]))
          tabs))]))
