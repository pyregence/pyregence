(ns pyregence.pages.dashboard
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <!]]
            [clojure.string                 :as string]
            [goog.string                    :refer [format]]
            [herb.core                      :refer [<class]]
            [pyregence.components.messaging :refer [message-box-modal
                                                    set-message-box-content!
                                                    toast-message!]]
            [pyregence.components.map-controls.icon-button :refer [icon-button]]
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
    (reset! match-drops (->> (u-async/call-clj-async! "get-match-drops" user-id)
                             (<!)
                             (:body)
                             (edn/read-string)
                             (sort-by :job-id #(> %1 %2))))))

(defn- delete-match-drop! [job-id]
  (go
    (<! (u-async/call-clj-async! "delete-match-drop" job-id))
    (get-user-match-drops @_user-id)
    (toast-message! (str "Match drop " job-id " has been deleted."))))

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

(defn- handle-delete-match-drop [job-id display-name]
  (let [message (str "Are you sure that you want to delete the Match Drop\n"
                     "with name \"%s\" and Job ID \"%s\"?\n"
                     "This action is irreversible.\n\n")]
    (set-message-box-content! {:mode   :confirm
                               :title  "Delete Match Drop"
                               :body   (format message display-name job-id)
                               :action #(delete-match-drop! job-id)})))

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
     [:td {:width "10%"} ; "Lon, Lat"
      (->> (select-keys common-args [:lon :lat])
         (vals)
         (map #(-> % (str) (subs 0 6)))
         (string/join ", "))]
     [:td (subs (:ignition-time common-args) 0 16)] ; "Ignition Time (UTC)"
     [:td (fmt-datetime created-at)] ; "Time Started (UTC)"
     [:td (fmt-datetime updated-at)] ; "Last Updated (UTC)"
     [:td (u-time/ms->hhmmss (- updated-at created-at))] ; "Elapsed Time"
     [:td [:a {:href "#" :on-click #(show-job-log-modal! job-id job-log)} "View Logs"]] ; "Logs"
     [:td ; "Delete"
      [:div {:style {:display "flex" :justify-content "center"}}
       [icon-button :trash
                    #(handle-delete-match-drop job-id display-name)
                    nil
                    :btn-size :circle]]]]))

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
           "Logs"
           "Delete"]]
   [:tbody
    (map (fn [{:keys [job-id] :as md}] ^{:key job-id}
           [match-drop-item md])
         @match-drops)]])

(defn- match-drop-header [user-id]
  [:div {:style {:display "flex" :justify-content "center"}}
   [:h3 {:style {:margin-bottom "0"}}
    "Match Drop Dashboard"]
   [:div {:style {:position "absolute" :right "6%"}}
    [icon-button :refresh #(get-user-match-drops user-id) "Refresh"]]])

(defn root-component
  "The root component for the match drop /dashboard page.
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
        [match-drop-header user-id]
        [:div {:style {:padding "1rem" :width "100%"}}
         [match-drop-table]]]])))
