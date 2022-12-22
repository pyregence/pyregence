(ns pyregence.components.map-controls.match-drop-tool
  (:require [clojure.core.async                    :refer [<! go timeout]]
            [clojure.edn                           :as edn]
            [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.common           :refer [input-datetime
                                                           input-hour
                                                           labeled-input
                                                           limited-date-picker]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.messaging        :refer [set-message-box-content!]]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.config                      :as c]
            [pyregence.state                       :as !]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.dom-utils             :as u-dom]
            [pyregence.utils.time-utils            :as u-time]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def poll?           (atom false))
(def available?      (r/atom false))
(def available-dates (r/atom {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- initiate-available-dates!
  "TODO"
  []
  (js/console.log "inside intiaite-available-dates")
  (go
    (if (-> (u-async/call-clj-async! "initiate-available-dates")
            (<!)
            (:success))
      (do
        (js/console.log "SUCCESS - available is true now")
        (reset! available? true))
      (do
        (js/console.log "Error when calling initiate")
        (println "Something went wrong when trying to call initiate-available-dates!")
        (reset! available? false)))))


(defn- set-available-dates!
  "Populates the available-dates atom with the result from calling
   get-available-dates on the back-end. Example:
   {:max-date-str '2021-05-27T18:00'
    :min-date-str '2022-12-02T18:00'}"
  []
  (js/console.log "inside set-available-dates")
  (when @available?
    (js/console.log "available is true")
    (go
      (js/console.log "Raw call: " (clj->js (edn/read-string (:body (<! (u-async/call-clj-async! "get-available-dates"))))))
      (reset! available-dates
              (-> (u-async/call-clj-async! "get-available-dates")
                (<!)
                (:body)
                (edn/read-string))))))

(defn- refresh-fire-names!
  "Updates the capabilities atom with all unique fires from the back-end
   layers atom, parsed into the proper format. An example value from
   get-fire-names can be seen below:
   {:foo {:opt-label \"foo\", :filter \"foo\", :auto-zoom? true,
    :bar {:opt-label \"bar\", :filter \"bar\", :auto-zoom? true}}"
  [user-id]
  (go
    (as-> (u-async/call-clj-async! "get-fire-names" user-id) fire-names
      (<! fire-names)
      (:body fire-names)
      (edn/read-string fire-names)
      (swap! !/capabilities update-in [:active-fire :params :fire-name :options] merge fire-names))))

(defn- poll-status
  "Continually polls for updated information about the match drop run every 5 seconds.
   Stops polling on finish or error signal."
  [match-job-id user-id]
  (go
    (while @poll?
      (let [{:keys [message md-status log]} (-> (u-async/call-clj-async! "get-md-status" match-job-id)
                                                (<!)
                                                (:body)
                                                (edn/read-string))]
        (case md-status
          0 (do
              (refresh-fire-names! user-id)
              (set-message-box-content! {:body (str "Finished running match-drop-" match-job-id ".")})
              (reset! poll? false))

          2 (set-message-box-content! {:body message})

          (do
            (println message)
            (js/console.error log)
            (set-message-box-content! {:body (str "Error running match-drop-" match-job-id ".\n\n" message)})
            (reset! poll? false))))
      (<! (timeout 5000)))))

(defn- initiate-match-drop!
  "Initiates the match drop run and initiates polling for updates."
  [display-name [lon lat] md-datetime user-id]
  (go
    (let [match-chan (u-async/call-clj-async! "initiate-md"
                                              {:display-name  (when-not (empty? display-name) display-name)
                                               :ignition-time (u-time/time-zone-iso-date md-datetime true)
                                               :lon           lon
                                               :lat           lat
                                               :user-id       user-id})]
      (set-message-box-content! {:title  "Processing Match Drop"
                                 :body   "Initiating match drop run."
                                 :mode   :close
                                 :action #(reset! poll? false)}) ; TODO the close button is for dev, disable on final product
      (let [{:keys [error match-job-id]} (edn/read-string (:body (<! match-chan)))]
        (if error
          (set-message-box-content! {:body (str "Error: " error)})
          (do (reset! poll? true)
              (poll-status match-job-id user-id)))))))

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
   [:div label]
   [:div#md-lonlat
    [:div#md-lon {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lat: " (cl-format nil "~,4f" (get lon-lat 1))]
    [:div#md-lat {:style {:display         "flex"
                          :flex            1
                          :justify-content "start"}}
     "Lon: " (cl-format nil "~,4f" (get lon-lat 0))]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn match-drop-tool
  "Match Drop Tool view. Enables a user to start a simulated fire at a particular
   location and date/time."
  [parent-box close-fn! user-id]
  (r/with-let [local-time-zone (u-time/get-time-zone (new js/Date))
               display-name    (r/atom "")
               lon-lat         (r/atom [0 0])
               md-datetime     (r/atom "")
               click-event     (mb/enqueue-marker-on-click! #(reset! lon-lat (first %)))
               _               (initiate-available-dates!)
               _               (set-available-dates!)]
    [:div#match-drop-tool
     [resizable-window
      parent-box
      400
      300
      "Match Drop Tool"
      close-fn!
      (fn [_ _]
        [:div {:style {:display "flex" :flex-direction "column" :height "inherit"}}
         [:div {:style {:flex-grow 1 :font-size "0.9rem" :margin "0.5rem 1rem"}}
          [:div {:style {:font-size "0.85rem" :margin "0.5rem 0"}}
           c/match-drop-instructions]
          [labeled-input "Name:" display-name {:placeholder "New Fire"}]
          [lon-lat-position $match-drop-location "Location" @lon-lat]
          (if (empty? @available-dates)
            (do
              (set-available-dates!)
              [:p "LOADING..."])
            [input-datetime
             (str "Date/Time (" local-time-zone ")")
             "md-datetime"
             @md-datetime
             (:min-date-str @available-dates)
             (:max-date-str @available-dates)
             #(reset! md-datetime (u-dom/input-value %))])
          [:div {:style {:display         "flex"
                         :flex-shrink     0
                         :justify-content "space-between"
                         :margin          "0.75rem 0 2.5rem"}}
           [:button {:class (<class $/p-themed-button)
                     :on-click #(do
                                  (set-available-dates!)
                                  (js/console.log "available dates are: " (clj->js @available-dates)))}
            "Get Dates"]
           [:button {:class    (<class $/p-themed-button)
                     :on-click #(js/window.open "/dashboard" "/dashboard")}
            "Dashboard"]
           [:button {:class    (<class $/p-button :bg-color :yellow :font-color :orange :white)
                     :disabled (or (= [0 0] @lon-lat) (= "" @md-datetime))
                     :on-click #(initiate-match-drop! @display-name @lon-lat @md-datetime user-id)}
            "Submit"]]]])]]
    (finally
      (mb/remove-event! click-event))))
