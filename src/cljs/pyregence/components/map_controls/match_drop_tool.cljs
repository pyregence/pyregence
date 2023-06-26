(ns pyregence.components.map-controls.match-drop-tool
  (:require [clojure.core.async                    :refer [<! go timeout]]
            [clojure.edn                           :as edn]
            [clojure.pprint                        :refer [cl-format]]
            [clojure.string                        :as str]
            [herb.core                             :refer [<class]]
            [pyregence.components.common           :refer [input-datetime
                                                           labeled-input
                                                           radio]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.messaging        :refer [set-message-box-content!]]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.state                       :as !]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.dom-utils             :as u-dom]
            [pyregence.utils.number-utils          :as u-num]
            [pyregence.utils.time-utils            :as u-time]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "Whether or not we should continue polling get-md-status on the back-end."}
  poll? (atom false))

(defn- reset-local-time-zone!
  [local-time-zone datetime]
  (reset! local-time-zone (->> datetime
                               (new js/Date)
                               (u-time/get-time-zone))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Match Drop Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def match-drop-instructions
  "Simulates a 24 hour fire using real-time weather data from the Hybrid model,
   which is a blend of the HRRR, NAM 3 km, and GFS 0.125\u00B0 models.
   Click on any CONUS location to \"drop\" a match, then set the date and time to begin
   the simulation. Chrome is currently the only supported browser for Match Drop.")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- set-md-available-dates!
  "Populates the !/md-available-dates atom with the result from calling get-available-dates
   on the back-end. Note that the date strings are populated in UTC using an ISO string.
   Example value of the !/md-available-dates atom after calling this function:
   {:historical {:min-date-iso-str \"2011-01-30T00:00Z\"
                 :max-date-iso-str \"2022-09-30T23:00Z\"}
    :forecast   {:min-date-iso-str \"2023-06-04T00:00Z\"
                 :max-date-iso-str \"2023-06-07T18:00Z\"}"
  []
  (go
    (let [get-md-available-dates (<! (u-async/call-clj-async! "get-md-available-dates"))]
      (if (:success get-md-available-dates)
        (reset! !/md-available-dates
                (-> get-md-available-dates
                    (:body)
                    (edn/read-string)))
        (reset! !/md-available-dates nil)))))

(defn- refresh-fire-names!
  "Updates the capabilities atom with all unique fires from the back-end
   layers atom, parsed into the proper format. Also updates the processed-params
   atom, which deals with the options available in the collapsible panel (so that
   a user can immediately select their fire without having to refresh the page
   or switching tabs to get the Match Drop to show up as an option). This is currently
   due to a limitation in state management (we seem to have some duplicated state
   in capabilities and processed-params).
   An example return value from get-fire-names can be seen below:
   {:foo {:opt-label \"foo\", :filter \"foo\", :auto-zoom? true}
    :bar {:opt-label \"bar\", :filter \"bar\", :auto-zoom? true}}"
  [user-id]
  (go
    (let [fire-names (->> (u-async/call-clj-async! "get-fire-names" user-id)
                          (<!)
                          (:body)
                          (edn/read-string))]
      (swap! !/capabilities update-in [:active-fire :params :fire-name :options] merge fire-names)
      (swap! !/processed-params update-in [:fire-name :options] merge fire-names))))

(defn- poll-status
  "Continually polls for updated information about the match drop run every 5 seconds.
   Stops polling on finish or error signal."
  [match-job-id user-id display-name ignition-time lat lon]
  (go
    (while @poll?
      (let [{:keys [message md-status log]} (-> (u-async/call-clj-async! "get-md-status" match-job-id)
                                                (<!)
                                                (:body)
                                                (edn/read-string))
            email (-> (u-async/call-clj-async! "get-email-by-user-id" user-id)
                      (<!)
                      (:body))]
        (case md-status
          0 (do
              (refresh-fire-names! user-id)
              (set-message-box-content! {:body (str "Finished running match-drop-" match-job-id ".")})
              (<! (u-async/call-clj-async! "send-email"
                                           email
                                           :match-drop
                                           {:match-job-id  match-job-id
                                            :display-name  display-name
                                            :fire-name     (str "match-drop-" match-job-id)
                                            :ignition-time ignition-time
                                            :lat           lat
                                            :lon           lon}))
              (reset! poll? false))

          1 (do
              (println message)
              (js/console.error log)
              (set-message-box-content! {:body (str "Error running match-drop-" match-job-id ".\n\n" message)})
              (reset! poll? false))

          (set-message-box-content! {:body message})))

      (<! (timeout 5000)))))

(defn- initiate-match-drop!
  "Initiates the match drop run and initiates polling for updates.
   Note that md-datetime-local is in local time and will be converted back
   to UTC before being passed to the back-end as the ignition-time."
  [display-name [lon lat] md-datetime-local forecast-weather? user-id]
  (go
    ;; Lat and Lon must be within CONUS
    ;; TODO we should also add a separate check for md-datetime-local being within the available weather dates
    (if (and (u-num/between? lon -125 -66)
             (u-num/between? lat 25 50))
      ;; Lat and Lon are valid, proceed
      (let [;; The below converts the local-time md-datetime-local string to UTC (e.g. turns "2023-06-06T09:40" into "2023-06-06 14:40 UTC") where the first string is in CDT
            ignition-time (u-time/date-string->iso-string md-datetime-local true)
            match-chan    (u-async/call-clj-async! "initiate-md"
                                                   {:display-name  (when-not (empty? display-name) display-name)
                                                    :ignition-time ignition-time
                                                    :lon           lon
                                                    :lat           lat
                                                    :wx-type       (if forecast-weather? "forecast" "historical")
                                                    :user-id       user-id})]
        (set-message-box-content! {:title         "Processing Match Drop"
                                   :body          "Initiating match drop run."
                                   :mode          :custom
                                   :custom-button [:button {:class    (<class $/p-form-button)
                                                            :on-click #(js/window.open "/dashboard" "/dashboard")}
                                                   "Dashboard"]})
        (let [{:keys [error match-job-id]} (edn/read-string (:body (<! match-chan)))]
          (if error
            (set-message-box-content! {:body (str "Error: " error)})
            (do (reset! poll? true)
                (poll-status match-job-id user-id display-name ignition-time lat lon)))))
      ;; Lat and Lon are invalid, let user know
      (set-message-box-content! {:title "Lat/Lon Error"
                                 :body  (str "Error: The Latitude of your ignition point must be between 25 and 50\n"
                                             " and its Longitude must be between -125 and -66.\n\n"
                                             "Please select another point and try again.")
                                 :mode :close}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $match-drop-location []
  ^{:combinators {[:> :div#md-lonlat] {:display "flex" :flex-direction "row"}
                  [:> :div#md-lonlat :div#md-lon] {:width "45%"}}}
  {:font-weight "bold"
   :margin      "0.5rem 0"})

(defn- $match-drop-cursor-position []
  {:display        "flex"
   :flex-direction "column"
   :font-size      "0.8rem"
   :font-weight    "bold"
   :margin         "0 1rem"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- lon-lat-position [$class label lon-lat]
  [:div {:class (<class $class)}
   [:div {:style {:font-size "1rem" :font-weight "bold"}} label]
   [:div#md-lonlat
    [:div#md-lon {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lat: " (cl-format nil "~,4f" (get lon-lat 1))]
    [:div#md-lat {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lon: " (cl-format nil "~,4f" (get lon-lat 0))]]])

(defn- weather-info [forecast-weather?]
  (let [pretty-date (fn [forecast? min?]
                      (let [datetime-string (as-> @!/md-available-dates %
                                                  (if forecast? (:forecast %) (:historical %))
                                                  (if min? (:min-date-iso-str %) (:max-date-iso-str %))
                                                  (u-time/iso-string->local-datetime-string %))]
                        [:strong
                         (str (str/replace datetime-string #"T" " ")
                              " " (u-time/get-time-zone (js/Date. datetime-string)))]))]
    [:div {:style {:font-size "0.85rem" :margin "0.5rem 0"}}
     (if @forecast-weather?
       [:p
        "The available Forecast Weather dates are: "
        [pretty-date true true]
        " to "
        [pretty-date true false]]
       [:p
        "The available Historical Weather dates are: "
        [pretty-date false true]
        " to "
        [pretty-date false false]
        "."])]))

(defn- weather-radio-buttons [forecast-weather? md-datetime-local local-time-zone]
  [:div {:style {:display "flex" :margin-bottom "0.8rem"}}
   [radio
    "Forecast Fire"
    @forecast-weather?
    true
    #(do (reset! forecast-weather? true)
         ;; When we switch to Forecast Weather data, default to the current date/time
         (reset! md-datetime-local (u-time/get-current-local-datetime-string))
         (reset-local-time-zone! local-time-zone @md-datetime-local))]
   [radio
    "Historical Fire"
    @forecast-weather?
    false
    #(do (reset! forecast-weather? false)
         ;; When we switch to Historical Weather data, default to the earliest historical weather date
         (reset! md-datetime-local (-> @!/md-available-dates
                                       (:historical)
                                       (:min-date-iso-str)
                                       (u-time/iso-string->local-datetime-string)))
         (reset-local-time-zone! local-time-zone @md-datetime-local))]])

(defn- datetime-local-picker [forecast-weather? md-datetime-local local-time-zone]
  [input-datetime
   (str (if @forecast-weather? "Forecast" "Historical")
        " Date/Time (" @local-time-zone ")")
   "md-datetime-local"
   @md-datetime-local
   (if @forecast-weather?
     (:min-date-iso-str (:forecast @!/md-available-dates))
     (:min-date-iso-str (:historical @!/md-available-dates)))
   (if @forecast-weather?
     (:max-date-iso-str (:forecast @!/md-available-dates))
     (:max-date-iso-str (:historical @!/md-available-dates)))
   #(do
      (reset! md-datetime-local (u-dom/input-value %))
      (reset-local-time-zone! local-time-zone (u-dom/input-value %)))])

(defn- md-buttons [md-datetime-local forecast-weather? display-name lon-lat user-id]
  [:div {:style {:display         "flex"
                 :flex-shrink     0
                 :justify-content "space-between"
                 :margin          "0.75rem 0 2.5rem"}}
   [:button {:class    (<class $/p-themed-button)
             :on-click #(js/window.open "/dashboard" "/dashboard")}
    "Dashboard"]
   [:button {:class    (<class $/p-button :bg-color :yellow :font-color :orange :white)
             :disabled (or (= [0 0] @lon-lat)
                           (= "" @md-datetime-local)
                           (empty? @!/md-available-dates))
             :on-click #(initiate-match-drop! @display-name @lon-lat @md-datetime-local @forecast-weather? user-id)}
    "Submit"]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn match-drop-tool
  "Match Drop Tool view. Enables a user to start a simulated fire at a particular
   location and date/time."
  [parent-box close-fn! user-id]
  (r/with-let [display-name      (r/atom "")
               lon-lat           (r/atom [0 0])
               forecast-weather? (r/atom true) ; Whether or not we are using forecast or historical weather data, default to using forecast
               md-datetime-local (r/atom (u-time/get-current-local-datetime-string)) ; Default to the current date/time
               local-time-zone   (r/atom (u-time/get-time-zone (js/Date. @md-datetime-local))) ; Default to the user's current time zone
               click-event       (mb/enqueue-marker-on-click! #(reset! lon-lat (first %)))
               _                 (set-md-available-dates!)]
    [:div#match-drop-tool
     [resizable-window
      parent-box
      560
      400
      "Match Drop Tool"
      close-fn!
      (fn [_ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :font-size "0.9rem" :margin "0.5rem 1rem"}}
          [:div {:style {:font-size "0.85rem" :margin "0.5rem 0"}}
           match-drop-instructions]
          [:hr {:style {:background "white"}}]
          [labeled-input "Fire Name:" display-name {:placeholder "New Fire"}]
          [lon-lat-position $match-drop-location "Ignition Location:" @lon-lat]
          [:hr {:style {:background "white"}}]
          (cond
            (nil? @!/md-available-dates)
            [:p
             "Something went wrong when loading the available weather dates. Please contact "
             [:a {:href "mailto:support@pyregence.org"} "support@pyregence.org"]
             "."]

            (empty? @!/md-available-dates)
            [:p "Loading available weather dates..."]

            (seq @!/md-available-dates)
            [:<>
             [weather-info forecast-weather?]
             [weather-radio-buttons forecast-weather? md-datetime-local local-time-zone]
             [datetime-local-picker forecast-weather? md-datetime-local local-time-zone]])
          [md-buttons md-datetime-local forecast-weather? display-name lon-lat user-id]]])]]
    (finally
      (mb/remove-markers!)
      (mb/remove-event! click-event))))
