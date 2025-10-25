(ns pyregence.components.settings.nav-bar
  (:require
   [clojure.string                  :as str]
   [clojure.walk                    :as walk]
   [herb.core                       :refer [<class]]
   [pyregence.components.svg-icons  :as svg]
   [pyregence.styles                :as $]
   [reagent.core                    :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CSS Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $on-hover-darker-orange-background []
  (with-meta {} {:pseudo {:hover {:background ($/color-picker :soft-orange)}}}))

(defn- $on-hover-darker-gray-border []

  (with-meta
    {:border        (str "1px solid " ($/color-picker :neutral-soft-gray))
     :border-radius "4px"
     ;;neutral-light-gray
     :background    "rgba(246, 246, 246, 1)"}
    ;; netural-md-gray
    {:pseudo {:hover {:border "1px solid rgba(118, 117, 117, 1)" :border-radius "4px"}
              ;; standard-orage
              :focus-within {:border "1px solid rgba(229, 177, 84, 1)" :border-radius "4px" :font-weight 500}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tabs
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- button
  [{:keys [icon text on-click selected?]}]
  [:nav-button {:on-click on-click
                :class (<class $on-hover-darker-orange-background)
                :style (cond-> {:display     "flex"
                                :height      "52px"
                                :padding     "16px"
                                :cursor      "pointer"
                                :gap         "12px"
                                :align-items "center"
                                :align-self  "stretch"}
                         selected?
                         (assoc
                          :background ($/color-picker :soft-orange)
                          :border-left (str "5px solid " ($/color-picker :standard-orange))))}
   (when icon [icon :height "24px" :width "24px"])
   [:p {:style {:color                ($/color-picker :neutral-dark-gray)
                :text-align                "justify"
                :font-family                "Roboto"
                :font-size                "16px"
                :font-style                "normal"
                :font-weight                "400"
                :line-height                "16px"
                :margin-bottom "0px"}} text]])

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
               :max-height     "572px"
               :overflow       "auto"
               :flex-direction "column"}
        selected?
        (assoc :background ($/color-picker :light-orange)))}
     [:div
      {:class    (<class $on-hover-darker-orange-background)
       :on-click on-click
       :style    {:display         "flex"
                  :align-items     "center"
                  :justify-content "space-between"
                  :width           "100%"
                  :padding-right   "16px"}}
      [button (dissoc m :selected? :on-click)]
      (if selected? [svg/arrow-up] [svg/arrow-down])]
     (when selected?
       [:<>
        [:div {:class (<class $on-hover-darker-gray-border)
               :style {:display        "flex"
                       :height         "42px"
                       :flex-direction "row"
                       :align-items    "center"
                       :margin         "16px"
                       ;;:border-radius  "4px"
                       ;; :neutral-light-gray
                       ;;:background     "rgba(246, 246, 246, 1)"

                       :cursor "pointer"}}
         [svg/search :height "16px" :width "16px"]
         [:input {:type        "text"
                  :placeholder "search"
                  :style       {:border     "none"
                                :background "transparent"
                                :width      "100%"
                                :outline    "none"}
                  :on-change   #(reset! search (.-value (.-target %)))}]]
        (doall
         (for [option options
               :when  (or (not @search) (same-letters-so-far? @search (:text option)))]
           [button option]))])]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tabs Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defmulti selected? :tab)
(defmulti on-click :tab)

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
;; Tab Configuration
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; This is where you configure the settings nav by adding tabs: buttons, drop downs ,etc..!

;; NOTE Each Tab's `:text` has to be unique because it's used as a component ID.
;; NOTE `drop-down`'s only support `button`s as options (not other drop downs).

(defn- tab-data->tab-descriptions
  "Returns a list of tab component descriptions from the provided `tab-data`."
  [{:keys [organizations]}]
  [{:tab  button
    :text "Account Settings"
    :icon svg/wheel}
   {:tab     drop-down
    :text    "Organization Settings"
    :options (->> organizations (map #(hash-map :tab button :text %)))
    :icon    svg/group}
   {:tab  button
    :text "Unaffilated Members"
    :icon svg/individual}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- tabs
  "Returns a list of tab components"
  [tab-data]
  (r/with-let [selected-log (r/atom [])]
    (->> tab-data
         tab-data->tab-descriptions
         ;; This keeps the Tab Configuration (above) minimal by adding implied data via a tree walk.
         (walk/postwalk
          (fn [tab]
            (if-not (and (map? tab) (:tab tab))
              tab
              (let [{:keys [text]} tab
                    id             (-> text
                                       str/lower-case
                                       (str/replace #"\s+" "-")
                                       keyword)
                    tab              (assoc tab :id id :key id :selected-log selected-log)]
                (assoc tab :selected? (selected? tab) :on-click (on-click tab))))))
         (mapv (fn [{:keys [tab] :as tab-data}] [tab tab-data]))
         (cons :<>)
         vec)))

(defn main
  [tabs-data]
  [:nav-bar-main {:style {:display         "flex"
                          :height          "100%"
                          :width           "360px"
                          :padding         "40px 0"
                          :flex-direction  "column"
                          :justify-content "space-between"
                          :border-right    (str "1px solid " ($/color-picker :neutral-soft-gray))
                          :background      "#FFF"}}
   [:div {:style {:display        "flex"
                  :flex-direction "column"
                  :border-top     (str "1px solid " ($/color-picker :neutral-soft-gray))
                  :border-bottom  (str "1px solid " ($/color-picker :neutral-soft-gray))}}
    [tabs tabs-data]]
   [button {:text "Logout" :icon svg/logout}]])
