(ns pyregence.components.forecast-tabs
  (:require
   [herb.core                   :refer [<class]]
   [pyregence.components.common :refer [tool-tip-wrapper]]
   [pyregence.datatypes.tab     :as tab]
   [pyregence.styles            :as $]))

;; This is needed so that we can show the tabs on the src/cljs/pyregence/pages/account_settings.cljs page
;; The duplication of code here with `config.cljs` is not ideal
(def default-forecast-tabs
  {:fuels        {:opt-label  "Fuels"
                  :hover-text "Layers related to fuel and potential fire behavior."}
   :fire-weather {:opt-label  "Weather"
                  :hover-text "Gridded weather forecasts from several US operational weather models including key parameters that affect wildfire behavior."}
   :fire-risk    {:opt-label  "Risk"
                  :hover-text "5-day forecast of fire consequence maps. Every day over 500 million hypothetical fires are ignited across California to evaluate potential fire risk.\n"}
   :active-fire  {:opt-label  "Active Fires"
                  :hover-text "14-day forecasts of active fires with burning areas established from satellite-based heat detection."}
   :psps-zonal {:opt-label  "PSPS"
                :hover-text "Public Safety Power Shutoffs (PSPS) zonal statistics."}})

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

(defn- published-tabs
  "What PyreCast published, as tabs. `capabilities` is a forecast's whole
   configuration and arrives keyed by forecast; only the four fields a tab is
   rendered and gated by come across."
  [capabilities]
  (map (fn [[forecast row]] (tab/->tab forecast row))
       (or capabilities default-forecast-tabs)))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts.
   Which of them a viewer is offered is `tab/offered-to?`, and is asked there so
   it can be tested without a browser."
  [{:keys [capabilities current-forecast on-forecast-select a-viewer mobile?]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (for [{:keys [forecast label hover-text] :as a-tab} (published-tabs capabilities)
          :when (tab/offered-to? a-tab a-viewer)]
      ^{:key forecast}
      [tool-tip-wrapper
       hover-text
       :top
       [:label {:style    ($forecast-label (= current-forecast forecast) mobile?)
                :class    (<class $/p-add-hover)
                :on-click #(on-forecast-select forecast)}
        label]]))])
