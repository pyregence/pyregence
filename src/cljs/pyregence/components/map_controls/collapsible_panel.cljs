(ns pyregence.components.map-controls.collapsible-panel
  (:require [reagent.core       :as r]
            [herb.core          :refer [<class]]
            [clojure.edn        :as edn]
            [clojure.core.async :refer [<! go go-loop]]
            [clojure.set        :as set]
            [pyregence.state    :as !]
            [pyregence.utils    :as u]
            [pyregence.styles   :as $]
            [pyregence.config   :as c]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.svg-icons :as svg]
            [pyregence.components.common    :refer [hs-str tool-tip-wrapper]]
            [pyregence.components.map-controls.panel-dropdown :refer [panel-dropdown]]
            [pyregence.components.map-controls.tool-button    :refer [tool-button]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-layer-name [geoserver-key filter-set update-layer!]
  (go
    (let [name (edn/read-string (:body (<! (u/call-clj-async! "get-layer-name"
                                                              geoserver-key
                                                              (pr-str filter-set)))))]
      (update-layer! :name name)
      name)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $collapsible-panel [show?]
  {:background-color ($/color-picker :bg-color)
   :box-shadow       (str "1px 0 5px " ($/color-picker :dark-gray 0.3))
   :color            ($/color-picker :font-color)
   :height           "100%"
   :left             (if show?
                       "0"
                       (if @!/mobile?
                         "calc(-100% - 2px)"
                         "calc(-18rem - 2px)"))
   :position         "absolute"
   :transition       "all 200ms ease-in"
   :width            (if @!/mobile? "100%" "18rem")
   :z-index          "101"})

(defn- $collapsible-button []
  {:background-color           ($/color-picker :bg-color)
   :border-bottom-right-radius "5px"
   :border-color               ($/color-picker :transparent)
   :border-style               "solid"
   :border-top-right-radius    "5px"
   :border-width               "0px"
   :box-shadow                 (str "3px 1px 4px 0 rgb(0, 0, 0, 0.25)")
   :cursor                     "pointer"
   :fill                       ($/color-picker :font-color)
   :height                     "40px"
   :padding                    "0"
   :width                      "28px"})

(defn- $collapsible-panel-body []
  (with-meta
    {:display         "flex"
     :flex-direction  "column"
     :height          "calc(100% - 3.25rem)"
     :justify-content "space-between"
     :overflow-y      "auto"}
    {:pseudo {:last-child {:padding-bottom "0.75rem"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- collapsible-panel-header []
  [:div#collapsible-panel-header
   {:style {:background-color ($/color-picker :header-color)
            :display          "flex"
            :justify-content  "space-between"
            :padding          "0.5rem 1rem"}}
   [:span {:style {:fill         ($/color-picker :font-color)
                   :height       "2rem"
                   :margin-right "0.5rem"
                   :width        "2rem"}}
    [svg/layers]]
   [:label {:style {:font-size "1.5rem"}}
    "Layer Selection"]
   [:span {:style {:margin-right "-.5rem"
                   :visibility   (if (and @!/show-panel? @!/mobile?) "visible" "hidden")}}
    [tool-button :close #(reset! !/show-panel? false)]]])

(defn- optional-layer [opt-label filter-set id z-index geoserver-key dependent-inputs disabled-for]
  (r/with-let [show?            (r/atom false)
               merge-filter-set (fn [init-filter-set dep-inputs]     ; Combines :dependent-inputs with the filter-set of an underlay.
                                  (as-> (@!/*forecast @!/*params) %  ; Should only be used with non-static underlays.
                                        (select-keys % dep-inputs)
                                        (vals %)
                                        (map name %)
                                        (set %)
                                        (set/union init-filter-set %)))
               add-params-watch (fn [show?]
                                  (add-watch !/*params :watch-*params
                                             (fn [key atom old-params new-params]
                                               (when show?
                                                 (go
                                                   (let [old-param-map      (@!/*forecast old-params)
                                                         new-param-map      (@!/*forecast new-params)
                                                         changed-keys       (u/get-changed-keys old-param-map new-param-map)
                                                         underlays          (get-in c/near-term-forecast-options [@!/*forecast :underlays])
                                                         filtered-underlays (into {} (filter (fn [[underlay attributes]]
                                                                                               (seq (set/intersection changed-keys (set (:dependent-inputs attributes)))))
                                                                                             underlays))]
                                                     (mb/set-multiple-layers-visibility! #"isochrones" false)
                                                     (doseq [[underlay attributes] filtered-underlays]
                                                       (let [cleaned-filter-set (merge-filter-set (:filter-set attributes) (:dependent-inputs attributes)) ; add in dependent-inputs to the filter-set of an underlay that isn't static
                                                             layer-id           (<! (get-layer-name geoserver-key cleaned-filter-set identity))]
                                                         (when (some? layer-id)
                                                           (when (not (mb/layer-exists? layer-id))
                                                             (mb/create-wms-layer! layer-id layer-id (:geoserver-key attributes) false (:z-index attributes)))
                                                           (mb/set-visible-by-title! layer-id show?))))))))))]
    [:div {:style {:margin-top ".5rem" :padding "0 .5rem"}}
     [:div#optional-layer-checkbox {:style {:display "flex"}}
      [:input {:id        id
               :style     {:margin ".25rem .5rem 0 0"}
               :disabled  (and (set? disabled-for) ; Disable the checbox if the currently selected inputs include at least one val from the :disabled-for set
                               (some (->> (@!/*forecast @!/*params)
                                          (vals)
                                          (filter keyword?)
                                          (set))
                                     disabled-for))
               :type      "checkbox"
               :checked   @show?
               :on-change #(go
                             (swap! show? not)
                             (add-params-watch @show?)
                             (let [cleaned-filter-set (if (some? dependent-inputs)
                                                        (merge-filter-set filter-set dependent-inputs)
                                                        filter-set)
                                   layer-id          (<! (get-layer-name geoserver-key cleaned-filter-set identity))]
                               (when (not (mb/layer-exists? layer-id))
                                 (mb/create-wms-layer! layer-id layer-id geoserver-key false z-index))
                               (mb/set-visible-by-title! layer-id @show?)))}]
      [:label {:for id} opt-label]]]
    (finally
      (remove-watch !/*params :watch-*params))))

(defn- optional-layers [underlays]
  (let [sorted-underlays (->> underlays
                              (map (fn [[id v]] (assoc v :id id)))
                              (sort-by :z-index)
                              (reverse))]
    [:<>
     [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
      [:div {:style {:display "flex" :justify-content "space-between"}}
       [:label "Optional Layers"]
       [tool-tip-wrapper
        [:div "Check the boxes below to display additional layers."
         [:div {:style {:margin-top 10}}
          [:hr]
          [:strong "Note"]
          ": The optional layers do not contain any data that can be queried by the "
          [:strong "Point Information "]
          "tool. Clicking on an area overlaid by an optional layer will display point information for the base layer beneath the point clicked and not for any selected optional layer(s)."]]
        :left
        [:div {:style ($/combine ($/fixed-size "1rem")
                                 {:margin "0 .25rem 4px 0"
                                  :fill   ($/color-picker :font-color)})}
         [svg/help]]]]
      (doall
       (map (fn [{:keys [id opt-label filter-set z-index enabled? geoserver-key dependent-inputs disabled-for]}]
              (when (or (nil? enabled?) (and (fn? enabled?) (enabled?)))
                ^{:key id}
                [optional-layer
                 opt-label
                 filter-set
                 id
                 z-index
                 geoserver-key
                 dependent-inputs
                 disabled-for]))
            sorted-underlays))]]))

(defn- collapsible-button []
  [:button
   {:style    ($collapsible-button)
    :on-click #(swap! !/show-panel? not)}
   [:div {:style {:align-items     "center"
                  :display         "flex"
                  :flex-direction  "column"
                  :justify-content "center"
                  :padding         "0.1rem"
                  :transform       (if @!/show-panel? "rotate(180deg)" "none")}}
    [svg/right-arrow]]])

(defn- collapsible-panel-toggle []
  [:div#collapsible-panel-toggle
   {:style {:display  (if (and @!/show-panel? @!/mobile?) "none" "block")
            :left     "100%"
            :position "absolute"
            :top      "50%"}}
   (if @!/mobile?
     [collapsible-button]
     [tool-tip-wrapper
      (str (hs-str @!/show-panel?) " layer selection")
      :left
      [collapsible-button]])])

(defn- collapsible-panel-section
  "A section component to differentiate content in the collapsible panel."
  [id body]
  [:section {:id    (str "section-" id)
             :style {:padding "0.75rem 0.6rem 0 0.6rem"}}
   [:div {:style {:background-color ($/color-picker :header-color 0.6)
                  :border-radius "8px"
                  :box-shadow    "0px 0px 3px #bbbbbb"
                  :padding       "0.5rem"}}
    body]])

(defn- help-section []
  [:div {:style {:display         "flex"
                 :justify-content "center"}}
   [:a {:href   "https://pyregence.org/wildfire-forecasting/data-repository/"
        :target "_blank"
        :style  {:color       ($/color-picker :font-color)
                 :font-family "Avenir"
                 :font-style  "italic"
                 :margin      "0"
                 :text-align  "center"}}
    "Learn more about the data."]])

(defn- opacity-input []
  [:div {:style {:margin-top "0.25rem"}}
   [:label (str "Opacity: " @!/active-opacity)]
   [:input {:style     {:width "100%"}
            :type      "range"
            :min       "0"
            :max       "100"
            :value     @!/active-opacity
            :on-change #(do (reset! !/active-opacity (u/input-int-value %))
                            (mb/set-opacity-by-title! "active" (/ @!/active-opacity 100.0)))}]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn collapsible-panel [*params select-param! underlays]
  (let [*base-map        (r/atom c/base-map-default)
        select-base-map! (fn [id]
                           (reset! *base-map id)
                           (mb/set-base-map-source! (get-in (c/base-map-options) [@*base-map :source])))]
    (reset! !/show-panel? (not @!/mobile?))
    (fn [*params select-param! underlays]
      (let [selected-param-set (->> *params (vals) (filter keyword?) (set))]
        [:div#collapsible-panel {:style ($collapsible-panel @!/show-panel?)}
         [collapsible-panel-toggle]
         [collapsible-panel-header]
         [:div#collapsible-panel-body {:class (<class $collapsible-panel-body)}
          [:div#section-wrapper
           [collapsible-panel-section
            "layer-selection"
            [:<>
             (map (fn [[key {:keys [opt-label hover-text options sort?]}]]
                    (let [sorted-options (if sort? (sort-by (comp :opt-label second) options) options)]
                      ^{:key hover-text}
                      [:<>
                       [panel-dropdown
                        opt-label
                        hover-text
                        (get *params key)
                        sorted-options
                        (= 1 (count sorted-options))
                        #(select-param! % key)
                        selected-param-set]]))
                  @!/processed-params)
             [opacity-input]]]
           [collapsible-panel-section
            "optional-layers"
            [optional-layers
             underlays]]
           [collapsible-panel-section
            "base-map"
            [panel-dropdown
             "Base Map"
             "Provided courtesy of Mapbox, we offer three map views. Select from the dropdown menu according to your preference."
             @*base-map
             (c/base-map-options)
             false
             select-base-map!]]]
          [collapsible-panel-section
           "help"
           [help-section]]]]))))
