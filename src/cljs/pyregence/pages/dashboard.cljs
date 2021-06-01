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
             (for [line (string/split log #"\\n")]
               [:div line])]
     :mode  :close}))

;; Styles

(defn $table []
  ^{:combinators {[:descendant :td] {:padding "0.2rem"}
                  [:descendant :th] {:padding "0.2rem"}}}
  {:border-collapse "separate"
   :border-spacing  "0.4rem"})

;; Components

(defn- thead [cols]
  [:thead
   [:tr
    (doall (map-indexed (fn [i col] ^{:key i} [:th col]) cols))]])

(defn- match-drop-item [{:keys [job-id md-status message created-at updated-at request log]}]
  [:tr
   [:td job-id]
   [:td md-status]
   [:td message]
   [:td (->> (select-keys request [:lon :lat]) (vals) (map #(-> % (str) (subs 0 6))) (string/join ", "))]
   [:td (str (:ignition-time request))]
   [:td (u/js-date->iso-string created-at true)]
   [:td (u/js-date->iso-string updated-at true)]
   [:td (u/ms->hhmmss (- updated-at created-at))]
   [:td [:a {:href "#" :on-click #(show-log-modal! job-id log)} "View Logs"]]])

(defn match-drop-table []
  [:table {:class (<class $table) :style {:width "100%"}}
   [thead ["ID" "Status" "Message" "Lon/Lat" "Ignition Time" "Time Started" "Last Updated" "Elapsed Time" "Logs"]]
   [:tbody
    (for [{:keys [job-id] :as md} @match-drops]
      ^{:key job-id}
      [match-drop-item md])]])

(defn- redirect-to-login! []
  (u/set-session-storage! {:redirect-from "/dashboard"})
  (u/jump-to-url! "/login"))

(defn root-component [{:keys [user-id]}]
  (when (nil? user-id) (redirect-to-login!))
  (reset! _user-id user-id)
  (user-match-drops user-id)
  (fn [_]
    [:div {:style ($/combine $/root {:padding  0
                                     :height   "100%"
                                     :position "relative"})}
     [message-box-modal]
     [:div {:style ($/combine $/flex-col {:padding "2rem 8rem"})}
      [:h3 "Dashboard"]
      [:div {:style {:padding "1rem"}}
       "Match Drops"]
      [:div {:style {:width     "100%"
                     :padding   "1rem"
                     :font-size "0.8rem"}}
       [match-drop-table]]
      [:div
       [:button {:class    "btn border-yellow text-brown"
                 :on-click #(user-match-drops user-id)}
        "Refresh"]]]]))
