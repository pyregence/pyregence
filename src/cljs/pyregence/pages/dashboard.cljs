(ns pyregence.pages.dashboard
  (:require [cljs.reader                    :as edn]
            [clojure.core.async             :refer [go <!]]
            [clojure.string                 :as string]
            [goog.string                    :refer [format]]
            [herb.core                      :refer [<class]]
            [lambdaisland.ansi              :refer [text->hiccup]]
            [pyregence.components.messaging :refer [message-box-modal
                                                    set-message-box-content!
                                                    toast-message!]]
            [pyregence.components.map-controls.icon-button :refer [icon-button]]
            [pyregence.components.svg-icons :as svg]
            [pyregence.styles               :as $]
            [pyregence.utils.browser-utils  :as u-browser]
            [pyregence.utils.async-utils    :as u-async]
            [pyregence.utils.time-utils     :as u-time]
            [reagent.core                   :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^:private _user-id    (r/atom nil))
(defonce ^:private match-drops (r/atom []))
(defonce ^:private ^{:doc "Whether or not the currently logged in user has match drop access."}
  match-drop-access? (r/atom false))

;; API Requests

(defn- set-user-match-drops! [user-id]
  (go
    (reset! match-drops (->> (u-async/call-clj-async! "get-match-drops" user-id)
                             (<!)
                             (:body)
                             (edn/read-string)
                             (sort-by :match-job-id #(> %1 %2))))))

(defn- delete-match-drop! [match-job-id]
  (go
    (if (:success (<! (u-async/call-clj-async! "delete-match-drop" match-job-id))) ; issue the back-end deletion
      (do
        (toast-message! (str "Match drop " match-job-id " is queued to be deleted. "
                             "Please click the 'Refresh' button."))
        (set-user-match-drops! @_user-id)) ; refresh the dashboard
      (toast-message! (str "Something went wrong while deleting Match Drop " match-job-id ".")))))

(defn- set-match-drop-access! [user-id]
  (go
   (let [response (<! (u-async/call-clj-async! "get-user-match-drop-access" user-id))]
     (if (:success response)
       (reset! match-drop-access? true)
       (reset! match-drop-access? false)))))

;; Helper

;; TODO make this bigger to reflect the long logs we have
(defn- show-job-log-modal! [match-job-id job-log]
  (set-message-box-content!
   {:title (str "Match Drop #" match-job-id)
    :body  [:div {:style {:max-height "400px"
                          :overflow-y "scroll"
                          :overflow-x "auto"}}
            [:pre {:style {:line-height 1.0 :margin-bottom 0 :max-width "50vw"}}
             (doall (map-indexed (fn [i line] ^{:key i}
                                   (text->hiccup line))
                                 (string/split job-log #"\\n")))]]
    :mode  :close}))

(defn- fmt-datetime [js-date]
  (-> js-date
      (u-time/js-date->iso-string true)
      (subs 0 16)
      (str ":" (u-time/pad-zero (.getSeconds js-date)))))

(defn- handle-delete-match-drop [match-job-id display-name]
  (let [message (str "Are you sure that you want to delete the Match Drop\n"
                     "with name \"%s\" and Job ID \"%s\"?\n"
                     "This action is irreversible.\n\n")]
    (set-message-box-content! {:mode   :confirm
                               :title  "Delete Match Drop"
                               :body   (format message display-name match-job-id)
                               :action #(delete-match-drop! match-job-id)})))

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

(defn- match-drop-item [{:keys [match-job-id
                                display-name
                                md-status
                                message
                                created-at
                                updated-at
                                dps-request
                                job-log]}]
  ;; NOTE if/when `common-args` is deprecated, we'll need to get these params from `dps-args` instead of `common-args`
  (let [{:keys [common-args]} (:script-args dps-request)]
    [:tr
     [:td match-job-id] ; "Job ID"
     [:td {:width "10%"} (when-not (nil? display-name) display-name)] ; "Fire Name"
     [:td ; "Match Drop Status"
      (condp = md-status
        0 "Completed!"
        1 "Error"
        2 "In progress..."
        3 "Removing...")]
     [:td {:width "25%"} ; "Message"
      [:pre {:style {:line-height 1.0 :margin-bottom 0 :max-width "550px" :overflow "auto"}}
       (when-not (nil? message) (text->hiccup message))]]
     [:td {:width "10%"} ; "Lon, Lat"
      (if-let [lon-lat (some->> (select-keys common-args [:lon :lat])
                                (vals)
                                (map #(-> % (str) (subs 0 6)))
                                (string/join ", "))]
        lon-lat
        "N/A")]
     [:td ; "Ignition Time (UTC)"
      (if (some? common-args)
        (subs (:ignition-time common-args) 0 16)
        "N/A")]
     [:td (fmt-datetime created-at)] ; "Time Started (UTC)"
     [:td (fmt-datetime updated-at)] ; "Last Updated (UTC)"
     [:td (u-time/ms->hhmmss (- updated-at created-at))] ; "Elapsed Time"
     [:td [:a {:href "#" :on-click #(show-job-log-modal! match-job-id job-log)} "View Logs"]] ; "Logs"
     [:td ; "Delete"
      [:div {:style {:display "flex" :justify-content "center"}}
       [icon-button :trash
                    #(handle-delete-match-drop match-job-id display-name)
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
    (map (fn [{:keys [match-job-id] :as md}] ^{:key match-job-id}
           [match-drop-item md])
         @match-drops)]])

(defn- match-drop-header [user-id]
  [:div {:style {:display "flex" :justify-content "center"}}
   [:h3 {:style {:margin-bottom "0"}}
    "Match Drop Dashboard"]
   [:div {:style {:position "absolute" :right "6%"}}
    [icon-button :refresh #(set-user-match-drops! user-id) "Refresh"]]])

(defn- no-match-drops []
  [:div {:style {:border        (str "2px solid " ($/color-picker :brown))
                 :border-radius "5px"
                 :display       "flex"
                 :fill          ($/color-picker :brown)
                 :margin-top    "3rem"
                 :padding       "1rem"}}
   [svg/exclamation-point :height "20px" :width "20px"]
   [:span {:style {:padding-left "0.5rem"}}
    "You don't have any Match Drops. Please return "
    [:a {:href "/"} "home"]
    " and use the Match Drop Tool to start a job."]])

(defn- no-access []
  [:div {:style ($/root)}
   [:div {:style {:border        (str "2px solid " ($/color-picker :brown))
                  :border-radius "5px"
                  :display       "flex"
                  :fill          ($/color-picker :brown)
                  :margin-top    "3rem"
                  :padding       "1rem"}}
     [svg/exclamation-point :height "20px" :width "20px"]
     [:span {:style {:padding-left "0.5rem"}}
      "You don't have access to use Match Drop. Please contact "
      [:a {:href "mailto:support@pyregence.org"} "support@pyregence.org"]
      " for more information."]]])

(defn root-component
  "The root component for the match drop /dashboard page.
   Displays a header, refresh button, and a table of a user's match drops "
  [{:keys [user-id]}]
  (reset! _user-id user-id)
  (set-user-match-drops! user-id)
  (set-match-drop-access! user-id)
  (fn [_]
    (cond
      (nil? user-id) ; User is not logged in
      (do (u-browser/redirect-to-login! "/dashboard")
          nil)

      (not @match-drop-access?) ; user doesn't have match drop access
      [no-access]

      :else  ; User is logged in and has match drop access
      [:div {:style ($/root)}
       [message-box-modal]
       [:div {:style ($/combine $/flex-col {:padding "2rem"})}
        [match-drop-header user-id]
        [:div {:style {:padding "1rem" :width "100%"}}
         (if (seq @match-drops)
           [match-drop-table]
           [no-match-drops])]]])))
