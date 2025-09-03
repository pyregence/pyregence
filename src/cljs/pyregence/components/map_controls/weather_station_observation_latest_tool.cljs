(ns pyregence.components.map-controls.weather-station-observation-latest-tool
  (:require [clojure.core.async                            :refer [take! go <!]]
            [clojure.string                                :as str]
            [herb.core                                     :refer [<class]]
            [pyregence.components.common                   :refer [tool-tip-wrapper]]
            [pyregence.components.help                     :as h]
            [pyregence.components.map-controls.tool-button :refer [tool-button]]
            [pyregence.components.mapbox                   :as mb]
            [pyregence.components.resizable-window         :refer [resizable-window]]
            [pyregence.components.svg-icons                :as svg]
            [pyregence.state                               :as !]
            [pyregence.styles                              :as $]
            [pyregence.utils.async-utils                   :as u-async]
            [pyregence.utils.time_utils                    :as u-time]
            [pyregence.utils.wmo-codes                     :refer [unit-id->labels]]
            [reagent.core                                  :as r]
            [cljs.core.async.interop                       :refer-macros [<p!]]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-weather-station-chan [weather-station]
  (u-async/fetch-and-process
   (str "https://api.weather.gov/stations/" (:stationIdentifier weather-station) "/observations/latest")
   {:method "get" :headers {"User-Agent" "support@sig-gis.com"}}
   (fn [response]
     (go
       (js->clj (aget (<p! (.json response)) "properties")
                :keywordize-keys true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $mobile []
  {:background-color ($/color-picker :bg-color)
   :box-shadow       (str "1px 0 5px " ($/color-picker :dark-gray 0.3))
   :color            ($/color-picker :font-color)
   :height           "290px"
   :display          "block"
   :width            "100%"
   :z-index          "105"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- mobile-header []
  [:div#collapsible-weather-station-header
   {:style {:align-items      "center"
            :background-color ($/color-picker :header-color)
            :display          "flex"
            :justify-content  "space-between"
            :padding             "0 1rem"}}
   [:span {:style {:fill         ($/color-picker :font-color)
                   :height       "1.5rem"
                   :margin-right "0.5rem"
                   :width        "1.5rem"}}
    [svg/weather-station]]
   [:label {:style {:font-size "1rem"}}
    "Weather Station Tool"]
   [:span {:style {:margin-right "-.5rem"
                   :visibility   (if (and @!/show-weather-station? @!/mobile?) "visible" "hidden")}}
    [tool-button :close #(reset! !/show-weather-station? false)]]])

(defn- intro []
  [:div {:style {:padding "1.2em"}}
   "Click on a weather station to view its latest observation. Powered by the "
   [:a {:href   "https://api.weather.gov/"
        :ref    "noreferrer noopener"
        :target "_blank"}
    "National Weather Service (NWS) API"]
   "."])

(defn- not-found
  [{:keys [name stationIdentifier]}]
  [:div
   [:div {:style {:display         "flex"
                  :flex-direction  "column"
                  :justify-content "flex-start"
                  :width           "100%"
                  :padding-top     "1rem"
                  :padding-left    "1rem"}}
    [:p "No information for the " name " weather station was found. Please check back later or try another station."]
    [:ul {:style {:padding-inline-start "1rem"}}
     [:li "Station ID: " stationIdentifier]
     [:li "Station name: " name]]]])

;;TODO this could share styles with the `not-found` component
(defn- info [{:keys [stationName stationId timestamp] :as latest-observation} reset-view zoom-weather-station]
  [:div
   ;;TODO what do we want to do if the observations go beyond the set window size?
   [:p {:style {:font-size     "1.1rem"
                :font-weight   "bold"
                :margin-bottom "0.3rem"
                :padding       "0.75rem 0 0 1rem"}}
    "Latest observation:"]
   [:div {:style {:display         "flex"
                  :justify-content "flex-start"
                  :width           "100%"
                  :padding-left    "1rem"}}
    ;;TODO when the user hovers on the observation it would be nice to pop up a longer description that includes a longer label
    [:ul {:style {:padding-inline-start "1rem"}}
     [:li "Station ID: " stationId]
     [:li "Station name: " stationName]
     [:li "Observed at: " (u-time/date-string->iso-string timestamp @!/show-utc?)]
     (let [CamelCase->title    (fn
                                 [CamelCase]
                                 (let [[f & r] (-> CamelCase
                                                   name
                                                   (str/split  #"(?=[A-Z])"))]
                                   (str/join " " (concat [(str/capitalize f)] (mapv str/lower-case r)))))
           unitCode->wmo-label #(-> %
                                    (str/split #":")
                                    last
                                    unit-id->labels
                                    (get "skos:altLabel"))
           observation->item   (fn [[observation-key {:keys [unitCode value]}]]
                                 (let [round-to-1-decimal #(/ (Math/round (* % 10)) 10)
                                       c->f               (fn [c] (+ (* c 1.8) 32))
                                       is-celsius?        (= unitCode "wmoUnit:degC")
                                       observation-param  (-> observation-key
                                                              CamelCase->title
                                                              (str/replace  #"last(\d+)" "last $1"))
                                       numeric-value      (when (number? value)
                                                            (if is-celsius?
                                                              (round-to-1-decimal (c->f value))
                                                              (round-to-1-decimal value)))
                                       units              (or ({"wmoUnit:km_h-1" "km/hr"
                                                                "wmoUnit:degC"   "\u00B0F"} ; note that this gets changed to Fahrenheit b/c we manually convert c->f above in the numeric-value binding
                                                               unitCode)
                                                              (unitCode->wmo-label unitCode))]
                                   (str observation-param ": " numeric-value units)))]
       (vec
        (cons :<>
              (->> latest-observation
                   (filter (fn [[_ {:keys [value]}]] value))
                   (map observation->item)
                   sort
                   (mapv (fn [i] [:li {:key (hash i)} i]))))))]]
   (when @!/terrain?
     [tool-tip-wrapper
      "Zoom Out to 2D"
      :top
      [:button {:class    (<class $/p-themed-button)
                :on-click reset-view
                :style    {:padding  "2px"}}
       [:div {:style {:height "32px"
                      :width  "32px"}}
        [svg/return]]]
      (fn [child]
        [:div {:style {:bottom   "1.25rem"
                       :position "absolute"
                       :left     "1rem"}}
         child])])

   [tool-tip-wrapper
    "Zoom Map to Weather Station"
    :top
    [:button {:class    (<class $/p-themed-button)
              :on-click zoom-weather-station
              :style    {:padding  "2px"}}
     [:div {:style {:height "32px"
                    :width  "32px"}}
      [svg/binoculars]]]
    (fn [child]
      [:div {:style {:bottom   "1.25rem"
                     :position "absolute"
                     :right    "1rem"}}
       child])]])

(defn- loading-all-stations []
  [:div {:style {:padding "1.2em"}}
   "Grabbing weather stations from the "
   [:a {:href   "https://api.weather.gov/"
        :ref    "noreferrer noopener"
        :target "_blank"}
    "National Weather Service (NWS) API"]
   "..."
   [:div {:style {:padding-top "1rem"}}
    "Please check back later."]])

(defn- loading-one-station [weather-station-name]
  [:div {:style {:padding "1.2em"}}
   (str "Loading the " weather-station-name " weather station...")])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool [parent-box close-fn!]
  (r/with-let [latest-observation   (r/atom nil)
               weather-station      (r/atom nil)
               zoom-weather-station (fn []
                                      (let [{:keys [longitude latitude]} @weather-station]
                                        (reset! !/terrain? true)
                                        (h/show-help! :terrain)
                                        (mb/toggle-dimensions! true)
                                        (mb/fly-to! {:center [longitude latitude]
                                                     :zoom   15})))
               reset-view           (fn []
                                      (let [{:keys [longitude latitude]} @weather-station]
                                        (reset! !/terrain? false)
                                        (mb/toggle-dimensions! false)
                                        (mb/fly-to! {:center [longitude latitude]
                                                     :zoom   6})))
               on-click             (fn [features]
                                      (go
                                        (when-let [new-weather-station (js->clj (aget features "properties") :keywordize-keys true)]
                                          (reset! weather-station new-weather-station)
                                          (let [observation-chan (get-weather-station-chan new-weather-station)]
                                            (reset! latest-observation
                                                    (or (<! observation-chan)
                                                        ;;TODO improve on how we handle the network calls
                                                        :error))))))
               ;; TODO, this form is sloppy.  Maybe return some value to store or convert to form 3 component.
               _                    (take! (mb/create-weather-station-layer! "weather-stations")
                                           #(mb/add-feature-highlight!
                                             "weather-stations" "weather-stations"
                                             :click-fn on-click))]

    (let [{:keys [stationName]
           :as latest-observation-info} @latest-observation
          render-content                (fn []
                                          (cond
                                            (empty? (:features @!/the-weather-stations))
                                            [loading-all-stations]

                                            (nil? @latest-observation)
                                            [intro]

                                            stationName
                                            [info
                                             latest-observation-info
                                             reset-view
                                             zoom-weather-station]

                                            (= @latest-observation :error)
                                            [not-found @weather-station]

                                            :else
                                            [loading-one-station stationName]))]
      (if @!/mobile?
        [:div#wildfire-mobile-weather-station-tool
         {:style ($/combine $/tool $mobile)}
         [mobile-header]
         [render-content]]
        [:div#wildfire-weather-station-tool
         [resizable-window
          parent-box
          325
          460
          "Weather Station Tool"
          close-fn!
          render-content]]))
    (finally
      (mb/remove-layer! "weather-stations")
      (mb/clear-highlight! "weather-stations" :selected))))
