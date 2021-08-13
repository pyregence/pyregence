(ns pyregence.pages.dashboard
  (:require [reagent.core :as r]
            [herb.core    :refer [<class]]
            [cljs.reader  :as edn]
            [clojure.core.async :refer [go <!]]
            [clojure.string     :as string]
            [pyregence.components.messaging :refer [set-message-box-content!
                                                    message-box-modal]]
            [pyregence.utils                :as u]
            [pyregence.styles               :as $]))

;; State

(defonce ^:private _user-id    (r/atom nil))
(defonce ^:private match-drops (r/atom []))

;; API Requests

(defn- user-match-drops [user-id]
  (go
    (reset! match-drops
            (edn/read-string (:body (<! (u/call-clj-async! "get-match-drops" user-id)))))))

;; Helper

(defn- show-log-modal! [job-id log]
  (set-message-box-content!
   {:title (str "Match Drop #" job-id)
    :body  [:div {:style {:max-height "300px"
                          :overflow-y "scroll"
                          :overflow-x "hidden"}}
            (doall (map-indexed (fn [i line] ^{:key i} [:div line])
                                (string/split log #"\\n")))]
    :mode  :close}))

(defn- fmt-datetime [js-date]
  (-> js-date
      (u/js-date->iso-string true)
      (subs 0 16)
      (str ":" (u/pad-zero (.getSeconds js-date)))))

;; Styles

(defn $table []
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

(defn- match-drop-item [{:keys [job-id display-name md-status message created-at updated-at request log]}]
  [:tr
   [:td display-name]
   [:td md-status]
   [:td message]
   [:td (->> (select-keys request [:lon :lat])
             (vals)
             (map #(-> % (str) (subs 0 6)))
             (string/join ", "))]
   [:td (subs (:ignition-time request) 0 16)]
   [:td (fmt-datetime created-at)]
   [:td (fmt-datetime updated-at)]
   [:td (u/ms->hhmmss (- updated-at created-at))]
   [:td [:a {:href "#" :on-click #(show-log-modal! job-id log)} "View Logs"]]])

(defn match-drop-table []
  [:table {:class (<class $table) :style {:width "100%"}}
   [thead ["Fire Name"
           "Status"
           "Message"
           "Lon/Lat"
           "Ignition Time (UTC)"
           "Time Started (UTC)"
           "Last Updated (UTC)"
           "Elapsed Time"
           "Logs"]]
   [:tbody
    (reverse (map (fn [{:keys [job-id] :as md}] ^{:key job-id} [match-drop-item md])
                  @match-drops))]])

(defn- redirect-to-login! []
  (u/set-session-storage! {:redirect-from "/dashboard"})
  (u/jump-to-url! "/login"))

(defn root-component [{:keys [user-id]}]
  (when (nil? user-id) (redirect-to-login!))
  (reset! _user-id user-id)
  (user-match-drops user-id)
  (fn [_]
    [:div {:style ($/combine $/root {:height   "100%"
                                     :padding  0
                                     :position "relative"})}
     [message-box-modal]
     [:div {:style ($/combine $/flex-col {:padding "2rem 8rem"})}
      [:div {:style {:display "flex"}}
       [:h3 {:style {:margin-bottom "0"
                     :margin-right  "1rem"}}
        "Match Drop Dashboard"]
       [:button {:class (<class $/p-form-button)
                 :on-click #(user-match-drops user-id)}
        "Refresh"]]
      [:div {:style {:padding "1rem"
                     :width   "100%"}}
       [match-drop-table]]]]))
