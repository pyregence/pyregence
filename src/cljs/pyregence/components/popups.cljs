(ns pyregence.components.popups
 (:require [clojure.core.async           :refer [<! go]]
           [clojure.string               :as cstr]
           [herb.core                    :refer [<class]]
           [pyregence.state              :as !]
           [pyregence.styles             :as $]
           [pyregence.utils.async-utils  :as u-async]
           [pyregence.utils.misc-utils   :as u-misc]
           [pyregence.utils.time-utils   :as u-time]
           [reagent.core                 :as r]
           [pyregence.components.svg-icons :as svg]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $popup-btn []
  (with-meta
    {:background    ($/color-picker :primary-standard-orange)
     :border        "none"
     :border-radius "3px"
     :color         ($/color-picker :black)
     :margin-top    "0.5rem"
     :padding       "0.25rem 0.5rem"}
    {:pseudo {:hover {:background-color ($/color-picker :yellow 0.8)}}}))

(defn- $popup-header []
  {:overflow      "hidden"
   :text-overflow "ellipsis"
   :white-space   "nowrap"
   :width         "180px"
   :font-size     "18px"
   :font-weight   "600"})

(defn- $popup-container [expanded?]
  (merge
    {:display        "flex"
     :flex-direction "column"
     :width          "200px"}
    (when expanded?
      {:max-height  "75vh"
       :white-space "normal"
       :width       "auto"
       :overflow    "auto"})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fire Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- fire-property [property value]
  [:div {:style {:display "flex" :height "14px" :gap "4px"}}
   [:span
    [:strong {:style {:color ($/color-picker :neutral-md-gray)}} property ": "]]
   [:span
    [:p {:style {:color       "black"
                 :font-weight "600"}} value]]])

(defn- fire-link [on-click]
  [:div {:style {:width "100%"}}
   [:button {:class    (<class $popup-btn)
             :on-click on-click}
    "View Forecast"]])

(defn fire-popup
  "Popup body for active fires."
  [{:keys [fire-name contain-per acres on-click show-link? sources]}]
  [:div {:style {:display        "flex"
                 :flex-direction "column"
                 :gap            "12px"}}
   [:h6 {:style ($popup-header)} fire-name]
   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :gap            "8px"}}
    [fire-property "Percent Contained" (str contain-per "%")]
    [fire-property "Acres Burned" (.toLocaleString acres)]
    [:strong {:style {:color ($/color-picker :neutral-md-gray)}} "Source(s):"]
    [:div {:style {:display        "flex"
                   :flex-direction "column"
                   :gap            "5px"}}
     (for [{:keys [icon name url]} sources]
       [:div {:style {:display          "flex"
                      :height           "52px"
                      :gap              "8px"
                      :background-color ($/color-picker :neutral-light-gray)
                      :padding          "8px 12px"
                      :align-items      "center"
                      :align-self       "stretch"
                      :border-radius    "8px"}}
        [icon]
        (when name
          [:p {:style
           ;; TODO remove this margin-bottom by figuring out why it has
           ;; a margin-bottom in the first place and likely changing that.
               {:margin-bottom "0px"}} name])
        [:a {:href "https://www.google.com" :target "_blank"}
         [svg/source-link]]])]]
   (when show-link? [fire-link on-click])])

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
   "instruction"
   "error"])

(defn- format-values
  "Formats red-flag description"
  [k value]
  (if (contains? #{"effective" "onset" "expires" "ends"} k)
   (u-time/date-string->iso-string value @!/show-utc?)
   (-> value
       (cstr/replace "..." ": ")
       (cstr/trim))))

(defn- properties->rows
  "Create popup rows from json object"
  [props]
  (let [json (js->clj props)]
    (for [k     red-flag-keys
          :let  [value (get json k)]
          :when (some? value)]
     ^{:key k}
      [fire-property (u-misc/camel->text k) (format-values k value)])))

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
  (r/with-let [info      (r/atom nil)
               expanded? (r/atom nil)]
      [:div {:class (<class $popup-container expanded?)}
       [:h6 {:style ($popup-header)} prod-type]
       (if @expanded?
        [:div
         [:hr]
         [:div (properties->rows @info)]]
        [:div
         [fire-property "Onset" (if (= onset "null") "N/A" onset)]
         [fire-property "Ends"  (if (= ends  "null") "N/A" ends)]])

       (when (seq url)
          [red-flag-link
           {:expanded? @expanded?
            :on-click (fn [_]
                        (if @expanded?
                          (do
                            (reset! info nil)
                            (reset! expanded? nil))
                          (go
                            (let [json (<! (get-red-flag-data url))]
                              (reset! expanded? true)
                              (reset! info (or (u-misc/try-js-aget json "properties")
                                               #js{:error "Error when fetching extra information"}))))))}])]))

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
