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
                :style    (cond-> {:display "flex"
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
      (cond-> {:display "flex"
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
      (if selected? [svg/up-arrow] [svg/down-arrow])]
     (when selected?
       [:<>
        [:div {:style {:height         "52px"
                       :display        "flex"
                       :flex-direction "row"
                       :align-items    "center"
                       :margin         "16px"
                       :border-radius  "10px"
                       :background     "white"}}
         [svg/search :height "24px" :width "24px"]
         [:input {:type      "text"
                  :style     {:border  "none"
                              :width   "100%"
                              :outline "none"}
                  :on-change #(reset! search (.-value (.-target %)))}]]
        (doall
         (for [option options
               :when  (or (not @search) (same-letters-so-far? @search (:text option)))]
           [button option]))])]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti selected? :cmpt)
(defmulti on-click :cmpt)

;; buttons are simple...

(defmethod selected? button
  [{:keys [id selected-log]}]
  (#{id} (last @selected-log)))

(defmethod on-click button
  [{:keys [selected-log id]}]
  #(swap! selected-log conj id))

;; drop-down require a considerable amount of book keeping to keep track of the
;; last known option for a drop down...

(defn- ->oids
  [options]
  (->> options
       (map :id)
       set))

(defn- last-selected-an-option?
  [selected-log options]
  ((->oids options)
   (last selected-log)))

(defn- get-option-id
  [selected-log options]
  (let [oids (->oids options)]
    (or (some oids (reverse selected-log))
        (first oids))))

(defmethod selected? drop-down
  [{:keys [selected-log options]}]
  (last-selected-an-option? @selected-log options))

(defmethod on-click drop-down
  [{:keys [selected-log options id]}]
  #(swap! selected-log (fn [sl]
                         (vec (concat sl
                                      [id]
                                      (when-not (last-selected-an-option? sl options)
                                        [(get-option-id sl options)]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; NOTE this only supports drop-downs having nested buttons,
;; any further nesting will require changes outside the config.

;; NOTE atm each `text` has to be unique because it's used as an ID.
;; (this can be added we hit this case!)

(defn- configure
  [{:keys [organizations]}]
  [{:cmpt button
    :text "Account Settings"
    :icon svg/wheel}
   {:cmpt    drop-down
    :text    "Organization Settings"
    :options (->> organizations (map #(hash-map :cmpt button :text %)))
    :icon    svg/group}
   {:cmpt button
    :text "Unaffilated Members"
    :icon svg/individual}])

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
            (if-not (and (map? m) (:cmpt m))
              m
              (let [{:keys [text]} m
                    id             (-> text
                                       str/lower-case
                                       (str/replace #"\s+" "-")
                                       keyword)
                    m              (assoc m :id id :key id :selected-log selected-log)]
                (assoc m :selected? (selected? m) :on-click (on-click m))))))
         (mapv (fn [{:keys [cmpt] :as m}] [cmpt  m]))
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
