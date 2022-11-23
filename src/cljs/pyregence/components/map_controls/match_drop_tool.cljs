(ns pyregence.components.map-controls.match-drop-tool
  (:require [clojure.core.async                    :refer [<! go timeout]]
            [clojure.edn                           :as edn]
            [clojure.pprint                        :refer [cl-format]]
            [herb.core                             :refer [<class]]
            [pyregence.components.common           :refer [labeled-input input-hour limited-date-picker]]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.messaging        :refer [set-message-box-content!]]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.config                      :as c]
            [pyregence.state                       :as !]
            [pyregence.styles                      :as $]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.time-utils            :as u-time]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def poll? (atom false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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
  [job-id user-id]
  (go
    (while @poll?
      (let [{:keys [message md-status log]} (-> (u-async/call-clj-async! "get-md-status" job-id)
                                                (<!)
                                                (:body)
                                                (edn/read-string))]
        (case md-status
          0 (do
              (refresh-fire-names! user-id)
              (set-message-box-content! {:body (str "Finished running match-drop-" job-id ".")})
              (reset! poll? false))

          2 (set-message-box-content! {:body message})

          (do
            (println message)
            (js/console.error log)
            (set-message-box-content! {:body (str "Error running match-drop-" job-id ".\n\n" message)})
            (reset! poll? false))))
      (<! (timeout 5000)))))

(defn- initiate-match-drop!
  "Initiates the match drop run and initiates polling for updates."
  [display-name [lon lat] md-date md-hour user-id]
  (go
    (let [datetime   (.toString (js/Date. (+ md-date (* md-hour 3600000))))
          match-chan (u-async/call-clj-async! "initiate-md"
                                              {:display-name  (when-not (empty? display-name) display-name)
                                               :ignition-time (u-time/time-zone-iso-date datetime true)
                                               :lon           lon
                                               :lat           lat
                                               :user-id       user-id})]
      (set-message-box-content! {:title  "Processing Match Drop"
                                 :body   "Initiating match drop run."
                                 :mode   :close
                                 :action #(reset! poll? false)}) ; TODO the close button is for dev, disable on final product
      (let [{:keys [error job-id]} (edn/read-string (:body (<! match-chan)))]
        (if error
          (set-message-box-content! {:body (str "Error: " error)})
          (do (reset! poll? true)
              (poll-status job-id user-id)))))))

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
  (r/with-let [display-name (r/atom "")
               lon-lat      (r/atom [0 0])
               md-date      (r/atom (u-time/current-date-ms)) ; Stored in milliseconds
               md-hour      (r/atom (.getHours (js/Date.))) ; hour (0-23) in the local timezone
               click-event  (mb/enqueue-marker-on-click! #(reset! lon-lat (first %)))]
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
          [:div {:style {:display "flex"}}
           [:div {:style {:flex "auto" :padding "0 0.5rem 0 0"}}
            [limited-date-picker "Forecast Date:" "md-date" md-date 7 0]]
           [:div {:style {:flex "auto" :padding "0 0 0 0.5rem"}}
            [input-hour "Start Time:" "md-time" md-hour]]]
          [:div {:style {:display         "flex"
                         :flex-shrink     0
                         :justify-content "space-between"
                         :margin          "0.75rem 0 2.5rem"}}
           [:button {:class    (<class $/p-themed-button)
                     :on-click #(js/window.open "/dashboard" "/dashboard")}
            "Dashboard"]
           [:button {:class    (<class $/p-button :bg-color :yellow :font-color :orange :white)
                     :disabled (or (= [0 0] @lon-lat) (nil? @md-date) (nil? @md-hour))
                     :on-click #(initiate-match-drop! @display-name @lon-lat @md-date @md-hour user-id)}
            "Submit"]]]])]]
    (finally
      (mb/remove-event! click-event))))
