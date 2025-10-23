(ns pyregence.components.settings.nav-bar
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [herb.core                            :refer [<class]]
   [pyregence.components.svg-icons       :as svg]
   [reagent.core                         :as r]))

;; css styles

(defn on-hover-darken-background []
  (with-meta {} {:pseudo {:hover {:background "#F8E8CC"}}}))

;; Components pieces

(defn button
  [{:keys [icon text on-click selected?]}]
  [:nav-button {:on-click on-click
                :class (<class on-hover-darken-background)
                :style (cond-> {:display "flex"
                                :height "52px"
                                :padding "16px"
                                :gap "12px"
                                :align-items "center"
                                :align-self "stretch"}
                         selected?
                         (assoc
                          :background "#F8E8CC"
                          :border-left "5px solid #E58154"))}
   (when icon [icon :height "24px" :width "24px"])
   [:label {:style {:color "#4A4A4A"
                    :text-align "justify"
                    :font-family "Roboto"
                    :font-size "16px"
                    :font-style "normal"
                    :font-weight "400"
                    :line-height "16px"
                    :text-transform "capitalize"}} text]])

(defn drop-down
  "A button that when clicked shows options"
  [{:keys [selected? options on-click] :as m}]
  [:nav-drop-down
   {:style
    (cond-> {:display "flex"
             :flex-direction "column"}
      selected?
      (assoc :background "#FBF4E6"))}
   [:div
    {:class  (<class on-hover-darken-background)
     :on-click on-click
     :style {:display "flex"
             :align-items "center"
             :justify-content "space-between"
             :padding-right "16px"
             :width "100%"}}
    [button (dissoc m :selected?)]
    (if selected?
      [svg/up-arrow :height "24px" :width "24px"]
      [svg/down-arrow :height "24px" :width "24px"])]
   (when selected?
     [:<>
      (for [option options]
        [button option])])])

;; Component functions

(defn get-last-selected-drop-down
  [{:keys [options selected-setting]}]
  (some (set (map :id options))
        (reverse @selected-setting)))

(defn was-last-selected-an-option?
  [selected-setting options]
  ((set (map :id options))
   (last selected-setting)))

(defmulti selected? :cmpt)

(defmethod selected? button
  [{:keys [id selected-setting]}]
  (#{id} (last @selected-setting)))

(defmethod selected? drop-down
  [{:keys [selected-setting options] :as m}]
  (when (was-last-selected-an-option? @selected-setting options)
    (get-last-selected-drop-down m)))

(defmulti on-click :cmpt)

(defmethod on-click button
  [{:keys [selected-setting id]}]
  #(swap! selected-setting conj id))

(defmethod on-click drop-down
  [{:keys [selected-setting options cmpt] :as m}]
  "on clicking the drop down header, it will clear the header if an option has already
   been selected, otherwise open to the last selected option, or default to the first."
  (if (and (= cmpt drop-down) (was-last-selected-an-option? @selected-setting options))
    #(reset! selected-setting [])
    #(swap! selected-setting conj
            (or (get-last-selected-drop-down m)
                (:id (first options))))))

;; Nav bar configuration

;;NOTE this only supports drop-downs having nested buttons,
;; any further nesting will require changes outside the config.
(def settings-config
  [{:cmpt button
    :text "Account Settings"
    :icon svg/wheel}
   {:cmpt    drop-down
    :text    "Organization Settings"
    :options [{:cmpt button
               :text "Org1"}
              {:cmpt button
               :text "Org2"}]
    :icon svg/group}
   {:cmpt button
    :text "Unaffilated Members"
    :icon svg/individual}])

;; page

(defn settings
  []
  (r/with-let [selected-setting (r/atom [])]
    (->> settings-config
         (walk/postwalk
          (fn [m]
            (if-not (and (map? m) (:cmpt m))
              m
              (let [{:keys [text]} m
                    id (-> text
                           str/lower-case
                           (str/replace #"\s+" "-")
                           keyword)
                    m (assoc m :id id :key id :selected-setting selected-setting)]
                (assoc m :selected? (selected? m) :on-click (on-click m))))))
         (mapv (fn [{:keys [cmpt] :as m}] [cmpt  m]))
         (cons :<>)
         vec)))

(defn main
  []
  [:nav-bar-main {:style {:display "flex"
                          :height "100%"
                          :width "360px"
                          :padding "40px 0"
                          :flex-direction "column"
                          :justify-content "space-between"
                          :border-right "1px solid #E1E1E1"
                          :backgrond "#FFF"}}
   [:div {:style {:display "flex"
                       :flex-direction "column"
                       :border-top "1px solid #E1E1E1"
                       :border-bottom "1px solid #E1E1E1"}}
    [settings]]
   [button {:text "Logout" :icon svg/logout}]])
