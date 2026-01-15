(ns pyregence.components.popups
 (:require [clojure.core.async           :refer [<! go]]
           [clojure.string               :as cstr]
           [herb.core                    :refer [<class]]
           [pyregence.styles             :as $]
           [pyregence.utils.async-utils  :as u-async]
           [pyregence.utils.misc-utils   :as u-misc]
           [reagent.core                 :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $popup-btn []
  (with-meta
    {:background    ($/color-picker :yellow)
     :border        "none"
     :border-radius "3px"
     :color         ($/color-picker :white)
     :margin-top    "0.5rem"
     :padding       "0.25rem 0.5rem"}
    {:pseudo {:hover {:background-color ($/color-picker :yellow 0.8)}}}))

(defn- $popup-header []
  {:overflow      "hidden"
   :text-overflow "ellipsis"
   :white-space   "nowrap"
   :width         "180px"})

(defn- $popup-container [expanded?]
  (merge
    {:display        "flex"
     :flex-direction "column"
     :width          (if expanded? "500px" "200px")}
    (when expanded?
      {:max-height  "75vh"
       :white-space "normal"
       :width       "auto"
       :overflow    "auto"})))

(defn- $info-section []
  {:margin-top  "10px"
   :padding-top "10px"
   :border-top  "1px solid rgba(0,0,0,0.12)"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fire Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fire-property [property value]
  [:div [:strong property ": "] value])

(defn- fire-link [on-click]
  [:div {:style {:text-align "right" :width "100%"}}
   [:button {:class    (<class $popup-btn)
             :on-click on-click}
    "Click to View Forecast"]])

(defn fire-popup
  "Popup body for active fires."
  [fire-name contain-per acres on-click show-link?]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:h6 {:style ($popup-header)}
    fire-name]
   [:div
    [fire-property "Percent Contained" (str contain-per "%")]
    [fire-property "Acres Burned" (.toLocaleString acres)]
    (when show-link? [fire-link on-click])]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Red-Flag Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private red-flag-keys
  ["description"
   "effective"
   "onset"
   "expires"
   "ends"
   "status"
   "messageType"
   "category"
   "severity"
   "certainty"
   "urgency"
   "event"
   "instruction"])

(defn- format-values
  "Formats red-flag description"
  [desc]
  (-> desc
      (cstr/replace "..." ": ")
      (cstr/trim)))

(defn- properties->rows
  "Create popup rows from json object"
  [props]
  (let [json (js->clj props)]
    (for [k     red-flag-keys
          :let  [value (get json k)]
          :when (some? value)]
      ^{:key k}
      [fire-property (u-misc/camel->text k) (format-values value)])))

(defn- get-red-flag-data
  "GET the red-flag URL and returns the parsed JSON on a channel"
  [url]
  (u-async/fetch-and-process
    url
    {:method "get"}
    (fn [response]
      (.json response))))

(defn- red-flag-link
  "Button component for fetching red-flag information"
  [{:keys [expanded? on-click]}]
  [:div {:style {:text-align "right" :width "100%"}}
   [:button {:class (<class $popup-btn)
             :on-click on-click}
    (if expanded? "Show Less Info" "Show More Info")]])

(defn red-flag-popup
  "Expandable popup for red-flag warning layer"
  [url prod-type onset ends]
  (r/with-let [info (r/atom nil)]
    (let [expanded? (some? @info)]
      [:div {:class (<class $popup-container expanded?)}
       [:h6 {:style ($popup-header)} prod-type]
       (if expanded?
         [:div {:class (<class $info-section)}
          [:div (properties->rows @info)]]
         [:div
          [fire-property "Onset" (if (= onset "null") "N/A" onset)]
          [fire-property "Ends"  (if (= ends  "null") "N/A" ends)]])

        (when (seq url)
          [red-flag-link
           {:expanded? expanded?
            :on-click (fn [_]
                        (if expanded?
                          (reset! info nil)
                          (go
                            (let [json (<! (get-red-flag-data url))]
                              (reset! info (u-misc/try-js-aget json "properties"))))))}])])))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fire History Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn fire-history-popup
  "Popup body for the fire history layer."
  [fire-name fire-year acres-burned]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:h6 {:style ($popup-header)}
    fire-name]
   [:div
    [fire-property "Fire Year" fire-year]
    [fire-property "Acres Burned" (.toLocaleString (Math/ceil acres-burned))]]])

;;TODO consider resusing this on fire-history and red-flag
(defn popup
  [{:keys [header options]}]
  [:div {:style {:display "flex" :flex-direction "column"}}
   [:h6 {:style ($popup-header)} header]
   (for [{:keys [label value]} options]
     ^{:key label}
     [:div  [:strong label  ": "] value])])
