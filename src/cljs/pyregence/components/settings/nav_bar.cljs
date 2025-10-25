(ns pyregence.components.settings.nav-bar
  (:require
   [clojure.string                  :as str]
   [clojure.walk                    :as walk]
   [herb.core                       :refer [<class]]
   [pyregence.components.svg-icons  :as svg]
   [reagent.core                    :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSS Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $on-hover-darken-background []
  (with-meta {} {:pseudo {:hover {:background "#F8E8CC"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- button
  [{:keys [icon text on-click selected?]}]
  [:nav-button {:on-click on-click
                :class    (<class $on-hover-darken-background)
                :style    (cond-> {:display     "flex"
                                   :height      "52px"
                                   :padding     "16px"
                                   :cursor      "pointer"
                                   :gap         "12px"
                                   :align-items "center"
                                   :align-self  "stretch"}
                            selected?
                            (assoc
                              :background "#F8E8CC"
                              :border-left "5px solid #E58154"))}
   (when icon [icon :height "24px" :width "24px"])
   [:label {:style {:color       "#4A4A4A"
                    :text-align  "justify"
                    :font-family "Roboto"
                    :font-size   "16px"
                    :font-style  "normal"
                    :font-weight "400"
                    :line-height "16px"}} text]])

(defn- same-letters-so-far? [s1 s2]
  (let [s1 (str/lower-case s1)
        s2 (str/lower-case s2)]
    (and (<= (count s1) (count s2))
         (= s1 (subs s2 0 (count s1))))))

(defn- drop-down
  [{:keys [selected? options on-click] :as m}]
  (r/with-let [search (r/atom nil)]
    [:nav-drop-down
     {:style
      (cond-> {:display        "flex"
               :cursor         "pointer"
               :max-height     "500px"
               :overflow       "auto"
               :flex-direction "column"}
        selected?
        (assoc :background "#FBF4E6"))}
     [:div
      {:class    (<class $on-hover-darken-background)
       :on-click on-click
       :style    {:display         "flex"
                  :align-items     "center"
                  :justify-content "space-between"
                  :padding-right   "16px"
                  :width           "100%"}}
      [button (dissoc m :selected? :on-click)]
      (if selected? [svg/arrow-up] [svg/arrow-down])]
     (when selected?
       [:<>
        [:div {:style {:display        "flex"
                       :height         "42px"
                       :flex-direction "row"
                       :align-items    "center"
                       :margin         "16px"
                       :border-radius  "4px"
                       :border         "1px solid #E1E1E1"
                       :background     "#F6F6F6"
                       :cursor         "pointer"}}
         [:div {:style {:padding "5px"}}]
         [svg/search :height "16px" :width "16px"]
         [:input {:type        "text"
                  :placeholder "search"
                  :style       {:border  "none"
                                :width   "100%"
                                :outline "none"}
                  :on-change   #(reset! search (.-value (.-target %)))}]]
        (doall
          (for [option options
                :when  (or (not @search) (same-letters-so-far? @search (:text option)))]
            [button option]))])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti selected? :component)
(defmulti on-click :component)

;; buttons are simple...

(defmethod selected? button
  [{:keys [id selected-log]}]
  (#{id} (last @selected-log)))

(defmethod on-click button
  [{:keys [selected-log id]}]
  #(swap! selected-log conj id))

(defn- ->option-ids
  [options]
  (->> options
       (map :id)
       set))

(defn- get-default-option-id
  [options]
  (->> options
       (map :id)
       first))

(defn- last-selected-was-a-drop-down-option?
  [selected-log options]
  ((->option-ids options)
   (last selected-log)))

(defmethod selected? drop-down
  [{:keys [selected-log options]}]
  "selected? is true if an option of this drop down was selected last."
  (last-selected-was-a-drop-down-option? @selected-log options))

(defmethod on-click drop-down
  [{:keys [selected-log options id]}]
  "Logs the id of the drop down, as well as the last selected or default when the drop down
   isn't already open aka the last selected item wasn't one of it's options."
  #(swap! selected-log
          (fn [sl]
            (vec (concat
                   sl
                   [id]
                   (when-not (last-selected-was-a-drop-down-option? sl options)
                     (let [oids (->option-ids options)]
                       [(or (some oids (reverse sl))
                            (get-default-option-id options))])))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE this only supports drop-downs having nested buttons,
;; any further nesting will require changes outside the config.

;; NOTE atm each `text` has to be unique because it's used as an ID.
;; (this can be added we hit this case!)

(defn- configure
  [{:keys [organizations]}]
  [{:component button
    :text      "Account Settings"
    :icon      svg/wheel}
   {:component drop-down
    :text      "Organization Settings"
    :options   (->> organizations (map #(hash-map :component button :text %)))
    :icon      svg/group}
   {:component button
    :text      "Unaffilated Members"
    :icon      svg/individual}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- settings
  [m]
  (r/with-let [selected-log (r/atom [])]
    (->> m
         configure
         (walk/postwalk
           (fn [m]
             (if-not (and (map? m) (:component m))
               m
               (let [{:keys [text]} m
                     id             (-> text
                                        str/lower-case
                                        (str/replace #"\s+" "-")
                                        keyword)
                     m              (assoc m :id id :key id :selected-log selected-log)]
                 (assoc m :selected? (selected? m) :on-click (on-click m))))))
         (mapv (fn [{:keys [component] :as m}] [component  m]))
         (cons :<>)
         vec)))

(defn main
  [m]
  [:nav-bar-main {:style {:display         "flex"
                          :height          "100%"
                          :width           "360px"
                          :padding         "40px 0"
                          :flex-direction  "column"
                          :justify-content "space-between"
                          :border-right    "1px solid #E1E1E1"
                          :backgrond       "#FFF"}}
   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :border-top     "1px solid #E1E1E1"
                  :border-bottom  "1px solid #E1E1E1"}}
    [settings m]]
   [button {:text "Logout" :icon svg/logout}]])
