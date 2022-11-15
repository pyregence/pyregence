(ns pyregence.pages.dashboard
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <!]]
            [clojure.string                 :as string]
            [herb.core                      :refer [<class]]
            [pyregence.components.messaging :refer [set-message-box-content! message-box-modal]]
            [pyregence.styles               :as $]
            [pyregence.utils.browser-utils  :as u-browser]
            [pyregence.utils.async-utils    :as u-async]
            [pyregence.utils.time-utils     :as u-time]
            [reagent.core                   :as r]))

;; State

(defonce ^:private _user-id    (r/atom nil))
(defonce ^:private match-drops (r/atom []))

;; API Requests

(defn- get-user-match-drops [user-id]
  (go
    (reset! match-drops
            (edn/read-string (:body (<! (u-async/call-clj-async! "get-match-drops" user-id)))))))

;; Helper
(defn- show-job-log-modal! [job-id job-log]
  (set-message-box-content!
   {:title (str "Match Drop #" job-id)
    :body  [:div {:style {:max-height "300px"
                          :overflow-y "scroll"
                          :overflow-x "hidden"}}
            (doall (map-indexed (fn [i line] ^{:key i} [:div line])
                                (string/split job-log #"\\n")))]
    :mode  :close}))

(defn- fmt-datetime [js-date]
  (-> js-date
      (u-time/js-date->iso-string true)
      (subs 0 16)
      (str ":" (u-time/pad-zero (.getSeconds js-date)))))

;; Styles

(defn- $table []
  ^{:combinators {[:descendant :td] {:border  "1px solid lightgray"
                                     :padding "0.4rem"}
                  [:descendant :th] {:border  "1px solid lightgray"
                                     :padding "0.4rem"}}}
  {:font-size "0.9rem"})

;; Components

(defn- thead [cols]
  [:thead
   [:tr
    (doall (map-indexed (fn [i col] ^{:key i} [:th col]) cols))]])

(defn- match-drop-item [{:keys [job-id display-name md-status message created-at updated-at request job-log]}]
  (let [{:keys [common-args]} (:script-args request)]
    [:tr
     [:td job-id] ; "Job ID"
     [:td {:width "10%"} display-name] ; "Fire Name"
     [:td md-status] ; "Status"
     [:td {:width "25%"} message] ; "Message"
     [:td {:width "10%"}
      (->> (select-keys common-args [:lon :lat]) ; "Lon, Lat"
         (vals)
         (map #(-> % (str) (subs 0 6)))
         (string/join ", "))]
     [:td (subs (:ignition-time common-args) 0 16)] ; "Ignition Time (UTC)"
     [:td (fmt-datetime created-at)] ; "Time Started (UTC)"
     [:td (fmt-datetime updated-at)] ; "Last Updated (UTC)"
     [:td (u-time/ms->hhmmss (- updated-at created-at))] ; "Elapsed Time"
     [:td [:a {:href "#" :on-click #(show-job-log-modal! job-id job-log)} "View Logs"]]])) ; "Logs"

(defn- match-drop-table []
  [:table {:class (<class $table) :style {:width "100%"}}
   [thead ["Job ID"
           "Fire Name"
           "Status"
           "Message"
           "Lon, Lat"
           "Ignition Time (UTC)"
           "Time Started (UTC)"
           "Last Updated (UTC)"
           "Elapsed Time"
           "Logs"]]
   [:tbody
    (reverse (map (fn [{:keys [job-id] :as md}] ^{:key job-id} [match-drop-item md])
                  @match-drops))]])

(defn root-component
  "The root comopnent for the match drop /dashboard page.
   Displays a header, refresh button, and a table of a user's match drops "
  [{:keys [user-id]}]
  (reset! _user-id user-id)
  (get-user-match-drops user-id)
  (fn [_]
    (cond
      ; TODO need to make sure the user is logged in AND verified to use Match Drop
      (nil? user-id) ; User is not logged in
      (do (u-browser/redirect-to-login! "/dashboard")
          nil)

      :else  ; User is logged in
      [:div {:style ($/root)}
       [message-box-modal]
       [:div {:style ($/combine $/flex-col {:padding "2rem"})}
        [:div {:style {:display "flex"}}
         [:h3 {:style {:margin-bottom "0"
                       :margin-right  "1rem"}}
          "Match Drop Dashboard"]
         [:button {:class    (<class $/p-form-button)
                   :on-click #(get-user-match-drops user-id)}
          "Refresh"]]
        [:div {:style {:padding "1rem"
                       :width   "100%"}}
         [match-drop-table]]]])))
