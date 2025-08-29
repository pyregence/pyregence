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
    "Wildfire Weather-Station Tool"]
   [:span {:style {:margin-right "-.5rem"
                   :visibility   (if (and @!/show-weather-station? @!/mobile?) "visible" "hidden")}}
    [tool-button :close #(reset! !/show-weather-station? false)]]])

(defn- intro []
  [:div {:style {:padding "1.2em"}}
   "Click on a weather station to view it's latest observation. Powered by the "
   [:a {:href   "https://api.weather.gov/"
        :ref    "noreferrer noopener"
        :target "_blank"}
    "National Weather Service (NWS) API."]
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
    [:p "No information for this weather station was found. Please check back later or try another station."]
    [:ul {:style {:padding-inline-start "1rem"}}
     [:li "Station ID: " stationIdentifier]
     [:li "Station name: " name]]]])

;;TODO this could share styles with the `not-found` component
(defn- info [{:keys [stationName stationId timestamp] :as latest-observation} reset-view zoom-weather-station]
  [:div
   ;;TODO what do we want to do if the observations go beyond the set window size?
   [:div {:style {:display         "flex"
                  :justify-content "flex-start"
                  :width           "100%"
                  :padding-top     "1rem"
                  :padding-left    "1rem"}}
    ;;TODO when the user hovers on the observation it would be nice to pop up a longer description that includes a longer label
    [:ul {:style {:padding-inline-start "1rem"}}
     [:li "Station ID: " stationId]
     [:li "Station name: " stationName]
     [:li "Observed at: " (u-time/date-string->iso-string timestamp true)]
     (let [CamelCase->title (fn
                              [CamelCase]
                              (let [[f & r] (-> CamelCase
                                                name
                                                (str/split  #"(?=[A-Z])"))]
                                (str/join " " (concat [(str/capitalize f)] (mapv str/lower-case r)))))
           unitCode->wmo-label #(-> %
                                    (clojure.string/split #":")
                                    last
                                    unit-id->labels
                                    (get "skos:altLabel"))
           ->item (fn [[k {:keys [unitCode value]}]]
                    (let [round-to-1-decimal  #(/ (Math/round (* % 10)) 10)]
                      (str (-> k
                               CamelCase->title
                               (str/replace  #"last(\d+)" "last $1"))
                           ": "
                           (if (float? value)
                             (round-to-1-decimal value)
                             value)
                           (or ({"wmoUnit:km_h-1" "km/hr"
                                 "wmoUnit:degC"   "\u00B0C"} unitCode)
                               (unitCode->wmo-label unitCode)))))]
       (vec
        (cons :<>
              (->> latest-observation
                   (filter (fn [[_ {:keys [value]}]] value))
                   (map ->item)
                   sort
                   (mapv (fn [i] [:li {:key (hash i)} i]))))))]]
   (when @!/terrain?
     [tool-tip-wrapper
      "Zoom Out to 2D"
      :left
      [:button {:class    (<class $/p-themed-button)
                :on-click reset-view
                :style    {:bottom   "1.25rem"
                           :padding  "2px"
                           :position "absolute"
                           :left     "1rem"}}
       [:div {:style {:height "32px"
                      :width  "32px"}}
        [svg/return]]]])
   [tool-tip-wrapper
    "Zoom Map to Weather-Station"
    :right
    [:button {:class    (<class $/p-themed-button)
              :on-click zoom-weather-station
              :style    {:bottom   "1.25rem"
                         :padding  "2px"
                         :position "absolute"
                         :right    "1rem"}}
     [:div {:style {:height "32px"
                    :width  "32px"}}
      [svg/binoculars]]]]])

(defn- loading [weather-station-name]
  [:div {:style {:padding "1.2em"}}
   (str "Loading weather-station " weather-station-name "...")])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool [parent-box close-fn!]
  (r/with-let [latest-observation  (r/atom nil)
               weather-station     (r/atom nil)
               exit-chan      (r/atom nil)
               zoom-weather-station    (fn []
                                         (let [{:keys [longitude latitude]} @weather-station]
                                           (reset! !/terrain? true)
                                           (h/show-help! :terrain)
                                           (mb/toggle-dimensions! true)
                                           (mb/fly-to! {:center  [longitude latitude]
                                                        :zoom    15}) 400))
               reset-view     (fn []
                                (let [{:keys [longitude latitude]} @weather-station]
                                  (reset! !/terrain? false)
                                  (mb/toggle-dimensions! false)
                                  (mb/fly-to! {:center [longitude latitude]
                                               :zoom   6})))
               on-click       (fn [features]
                                (go
                                  (when-let [new-weather-station (js->clj (aget features "properties") :keywordize-keys true)]
                                    (u-async/stop-refresh! @exit-chan)
                                    (reset! weather-station new-weather-station)
                                    (reset! latest-observation
                                            (or
                                             (<! (u-async/fetch-and-process
                                                  (str "https://api.weather.gov/stations/" (:stationIdentifier new-weather-station) "/observations/latest")
                                                  {:method "get" :headers {"User-Agent" "support@sig-gis.com"}}
                                                  (fn [response]
                                                    (go
                                                      (js->clj (aget (<p! (.json response)) "properties")
                                                               :keywordize-keys true)))))
                                              ;;TODO improve on how we handle the networkcalls
                                             :error)))))
               ;; TODO, this form is sloppy.  Maybe return some value to store or convert to form 3 component.
               _              (take! (mb/create-weather-station-layer! "weather-stations")
                                     #(mb/add-feature-highlight!
                                       "weather-stations" "weather-stations"
                                       :click-fn on-click))]

    (let [{:keys [stationName] :as latest-observation-info} @latest-observation
          render-content     (fn []
                               ;;TODO double check these conditionals make sense when viewed
                               ;;in the larger flow.
                               (cond
                                 (nil? @latest-observation)
                                 [intro]

                                 stationName
                                 [info
                                  latest-observation-info
                                  reset-view
                                  zoom-weather-station]

                                 @weather-station
                                 [not-found @weather-station]

                                 :else
                                 [loading stationName]))]
      (if @!/mobile?
        [:div#wildfire-mobile-weather-station-tool
         {:style ($/combine $/tool $mobile)}
         [mobile-header]
         [render-content]]
        [:div#wildfire-weather-station-tool
         [resizable-window
          parent-box
          290
          460
          "Weather Station's latest observation"
          close-fn!
          render-content]]))
    (finally
      (u-async/stop-refresh! @exit-chan)
      (mb/remove-layer! "weather-stations")
      (mb/clear-highlight! "weather-stations" :selected))))
