(ns pyregence.components.settings.nav-bar
  (:require
   [clojure.string :as str]
   [clojure.walk :as walk]
   [pyregence.components.svg-icons       :as svg]
   [reagent.core                         :as r]))

(defn button
  [{:keys [icon text tag on-click selected?]}]
  [tag {:on-click on-click
        :style (cond-> {:display "flex"
                        :height "52px"
                        :padding "16px"
                        :align-self "stretch"}
                 selected?
                 (assoc
                  :background "#F8E8CC"
                  :border-left "5px solid #E58154"))}
   [:f1739327813 {:style {:display "flex"
                          :align-items "center"
                          :gap "12px"}}
    ;;TODO don't use the this conditional instead compose.
    (when icon
      [icon :height "24px" :width "24px"])
    [:label {:style {:color "#4A4A4A"
                     :text-align "justify"
                     :font-family "Roboto"
                     :font-size "16px"
                     :font-style "normal"
                     :font-weight "400"
                     :line-height "16px"
                     :text-transform "capitalize"}} text]]])

(defn drop-down
  "A button that when clicked shows options"
  [{:keys [selected? tag options] :as m}]
  ;;TODO each of these tags needs a specific name
  [tag
   {:style
    (cond-> {:display "flex"
             :flex-direction "column"}
      selected?
      (assoc
       :background "#F8E8CC"))}
   [tag
    {:style (cond-> {:display "flex"
                     :align-items "center"
                     :justify-content "space-between"
                     :padding-right "16px"
                     :width "100%"}
              selected?
             ;;TODO share this somehow with the button
              (assoc
               :background "#F8E8CC"
               ;;TODO do we select the header border? I don't think so
               #_#_:border-left "5px solid #E58154"))}
    [button (dissoc m :selected?)]
    (if selected?
      [svg/up-arrow :height "24px" :width "24px"]
      [svg/down-arrow :height "24px" :width "24px"])]
   (when selected?
     [:<>
      (for [option options]
        [button option])])])

(defn text->id
  [text]
  (-> text
      str/lower-case
      (str/replace #"\s+" "-")
      keyword))

(defn add-selected-handlers
  [m selected])

(def settings-config
  [{:type button
    :text "Account Settings"
    :icon svg/wheel}
   {:type    drop-down
    :text    "Organization Settings"
    :options [{:type button
               :text "Org1"}
              {:type button
               :text "Org2"}]
    :icon svg/group}
   {:type button
    :text "Unaffilated Members"
    :icon svg/individual}])

(defmulti selected? :type)

(defmethod selected? button
  [{:keys [id selected-setting]}]
  (#{id} @selected-setting))

(defmethod selected? drop-down
  [{:keys [options selected-setting]}]
  ((set (map :id options)) @selected-setting))

(defmulti on-click :type)

(defmethod on-click button
  [{:keys [selected-setting id]}]
  #(reset! selected-setting id))

(defmethod on-click drop-down
  [{:keys [selected-setting options] :as m}]
  ;;TODO improve this to remember the last-selected-option
  #(reset! selected-setting (:id (first options))))

(defn settings
  []
  (r/with-let [selected-setting (r/atom nil)]
    (->> settings-config
         (walk/postwalk
          (fn [m]
            (if-not (and (map? m) (:type m))
              m
              (let [{:keys [type text]} m
                    id (text->id text)
                    m (assoc m :tag id :id id :selected-setting selected-setting)]
                (assoc m :selected? (selected? m) :on-click (on-click m))))))
         (mapv (fn [{:keys [type] :as m}] [type m]))
         (cons :<>)
         vec)))


#_(defn settings
    []
    (r/with-let [selected-setting (r/atom nil)]
      (def selected-setting @selected-setting)
      [:<>
       [button
        (let [id :account-settings]
          {:text "Account Settings"
           :icon svg/wheel
           :tag id
           :on-click #(reset! selected-setting id)
           :selected? (#{id} @selected-setting)})]
      ;;TODO handle on click correctly
       [drop-down
        (let [id :organization-settings]
          {:icon svg/group
           :tag id
           :text "Organization Settings"
           :selected? (#{:org1 :org2} @selected-setting)
           :on-click #(reset! selected-setting :org1)
           :options [{:text "org1"
                      :tag :org1
                      :on-click #(reset! selected-setting :org1)
                      :selected? (= @selected-setting :org1)}
                     {:text "org2"
                      :tag :org2
                      :on-click #(reset! selected-setting :org2)
                      :selected? (= @selected-setting :org2)}]})]
       [button
        (let [id :unaffiliated-members]
          {:text "Unaffiliated Members"
           :icon svg/individual
           :tag id
           :on-click #(reset! selected-setting id)
           :selected? (#{id} @selected-setting)})]]))

(defn main
  []
  [:nav-bar {:style {:display "flex"
                     :height "100%"
                     :width "360px"
                     :padding "40px 0"
                     :flex-direction "column"
                     :justify-content "space-between"
                     :border-right "1px solid #E1E1E1"
                     :backgrond "#FFF"}}
   [:settings {:style {:display "flex"
                       :flex-direction "column"
                       :border-top "1px solid #E1E1E1"
                       :border-bottom "1px solid #E1E1E1"}}
    [settings]]
   [button {:text "Logout" :icon svg/logout :tag :logout}]])
