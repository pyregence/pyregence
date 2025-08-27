(ns pyregence.components.map-controls.weather-station-tool
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
            [pyregence.wmo-codes-registry-2025-8-27        :refer [wmo-unit-id->labels]]
            [reagent.core                                  :as r]
            [cljs.core.async.interop                       :refer-macros [<p!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-weather-station-image-chan [active-weather-station]
  (go
    (->> (u-async/call-clj-async! "get-current-image"
                                  :post-blob
                                  (:name active-weather-station)
                                  (:api-name active-weather-station))
         (<!)
         (:body)
         (js/URL.createObjectURL))))

(defn- alert-image-url->alert-weather-station-id
  "Parses the weather-station ID out of the image URL for AlertWest weather-stations.
   Ex: A URL of \"https://prod.weathernode.net/data/img/2428/2023/07/12/Sutro_Tower_1_1689204279_6490.jpg\"
   returns `2428`."
  [url]
  (-> url
      (str/split #"/")
      (get 5 nil)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $alert-logo-style []
  {:height    "auto"
   :left      "2rem"
   :min-width "100px"
   :position  "absolute"
   :top       "2rem"
   :width     "10%"})

(defn- $mobile-weather-station-tool []
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

(defn- mobile-weather-station-tool-header []
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

(defn- weather-station-tool-intro []
  [:div {:style {:padding "1.2em"}}
   "Click on a weather station to view the latest observation. Powered by "
   [:a {:href   "https://api.weather.gov/"
        :ref    "noreferrer noopener"
        :target "_blank"}
    "Weather.gov"]
   "."])

(defn- weather-station-info [{:keys [stationName stationId timestamp] :as latest-observation} reset-view zoom-weather-station]
  [:div
   [:div {:style {:display         "flex"
                  :justify-content "flex-start"
                  :width           "100%"
                  :margin-top      "1rem"}}
    ;;TODO when the user hovers on the observation it would be nice to pop up a longer description that includes a longer label
    [:ul
     [:li "Station id: " stationId]
     [:li "Station name: " stationName]
     [:li "observed at: " (u-time/date-string->iso-string timestamp true)]
     (let [unitCode->wmo-label #(-> %
                                    (clojure.string/split #":")
                                    last
                                    wmo-unit-id->labels
                                    (get "skos:altLabel"))
           show                (fn [[k label]]
                                 (let [{:keys [unitCode value]} (latest-observation k)
                                       round-to-1-decimal  #(/ (Math/round (* % 10)) 10)]

                                   [:li
                                    {:key k}
                                    ;;TODO this conditional logic should probably be in a data structure
                                    (str label
                                         ": "
                                         (if (float? value)
                                           (round-to-1-decimal value)
                                           value)
                                         (or ({"wmoUnit:km_h-1" "km/hr"
                                               "wmoUnit:degC"   "\u00B0C"} unitCode)
                                             (unitCode->wmo-label unitCode)))]))]
       (vec
        (cons :<>
              (->> [[:temperature "Temperature"]
                    [:relativeHumidity "Relative humidity"]
                    [:dewpoint "Dewpoint"]
                    [:windSpeed "Wind speed"]
                    [:windDirection "Wind direction"]
                    [:windGust "Wind gust"]]
                   (filter (fn [[k _]] (-> k latest-observation :value)))
                   (mapv show)))))]]
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

(defn- loading-weather-station [weather-station-name]
  [:div {:style {:padding "1.2em"}}
   (str "Loading weather-station " weather-station-name "...")])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn weather-station-tool [parent-box close-fn!]
  (r/with-let [active-weather-station  (r/atom nil)
               image-src      (r/atom nil)
               exit-chan      (r/atom nil)
               zoom-weather-station    (fn []
                                         (let [{:keys [longitude latitude tilt pan]} @active-weather-station]
                                           (reset! !/terrain? true)
                                           (h/show-help! :terrain)
                                           (mb/toggle-dimensions! true)
                                           (mb/fly-to! {:center  [longitude latitude]
                                                        :zoom    15
                                                        :bearing pan
                                                        :pitch   (min (+ 90 tilt) 85)}) 400))
               reset-view     (fn []
                                (let [{:keys [longitude latitude]} @active-weather-station]
                                  (reset! !/terrain? false)
                                  (mb/toggle-dimensions! false)
                                  (mb/fly-to! {:center [longitude latitude]
                                               :zoom   6})))
               on-click       (fn [features]
                                (go
                                  (when-let [new-weather-station (js->clj (aget features "properties") :keywordize-keys true)]
                                    (u-async/stop-refresh! @exit-chan)
                                    (<! (u-async/fetch-and-process
                                         (str "https://api.weather.gov/stations/" (:stationIdentifier new-weather-station) "/observations/latest")
                                         {:method "get" :headers {"User-Agent" "support@sig-gis.com"}}
                                         (fn [response]
                                           (go
                                             (reset! active-weather-station
                                                     (js->clj
                                                      (aget

                                                       (<p! (.json response))
                                                       "properties")
                                                      :keywordize-keys true))))))

                                    (reset! image-src nil)
                                    (let [image-chan (get-weather-station-image-chan @active-weather-station)]
                                      (reset! image-src (<! image-chan))
                                      (reset! exit-chan
                                              (u-async/refresh-on-interval! #(go (reset! image-src (<! (get-weather-station-image-chan @active-weather-station))))
                                                                            60000))))))
               ;; TODO, this form is sloppy.  Maybe return some value to store or convert to form 3 component.
               _              (take! (mb/create-weather-station-layer! "fire-weather-stations")
                                     #(mb/add-feature-highlight!
                                       "fire-weather-stations" "fire-weather-stations"
                                       :click-fn on-click))]
    (let [{:keys [stationName] :as latest-observation} @active-weather-station
          render-content     (fn []
                               (cond
                                 (nil? @active-weather-station)
                                 [weather-station-tool-intro]

                                 @image-src
                                 [weather-station-info
                                  latest-observation
                                  reset-view
                                  zoom-weather-station]

                                 :else
                                 [loading-weather-station stationName]))]
      (if @!/mobile?
        [:div#wildfire-mobile-weather-station-tool
         {:style ($/combine $/tool $mobile-weather-station-tool)}
         [mobile-weather-station-tool-header]
         [render-content]]
        [:div#wildfire-weather-station-tool
         [resizable-window
          parent-box
          290
          460
          "Station's latest observation"
          close-fn!
          render-content]]))
    (finally
      (u-async/stop-refresh! @exit-chan)
      (mb/remove-layer! "fire-weather-stations")
      (mb/clear-highlight! "fire-weather-stations" :selected))))
