(ns pyregence.components.zoom-bar
  (:require [reagent.core        :as r]
            [herb.core           :refer [<class]]
            [pyregence.state     :as !]
            [pyregence.styles    :as $]
            [pyregence.utils     :as u]
            [pyregence.components.mapbox    :as mb]
            [pyregence.components.help      :as h]
            [pyregence.components.common           :refer [tool-tip-wrapper]]
            [pyregence.components.tool-button      :refer [tool-button]]
            [pyregence.components.tool-bar         :refer [$tool-bar]]
            [pyregence.components.messaging        :refer [set-message-box-content!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Help
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ed-str [enabled?]
  (if enabled? "Disable" "Enable"))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Share Tool
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $share-link []
  (if @!/mobile?
    {:position "absolute"
     :width    "1rem"
     :z-index  "-1"}
    {:width "100%"}))

(defn- share-inner-modal [create-share-link]
  (r/with-let [copied     (r/atom false)
               share-link (create-share-link)
               on-click   #(do
                             (u/copy-input-clipboard! "share-link")
                             (reset! copied true))]
    [:div {:style (if @!/mobile?
                    {:display         "flex"
                     :justify-content "center"}
                    ($/combine $/flex-row {:width "100%"}))}
     [:input {:id         "share-link"
              :style      ($share-link)
              :auto-focus true
              :read-only  true
              :type       "text"
              :value      share-link
              :on-click   on-click}]
     [:input {:class    (<class $/p-form-button)
              :style    (when-not @!/mobile? {:margin-left "0.9rem"})
              :type     "button"
              :value    (if @copied "Copied!" "Copy URL")
              :on-click on-click}]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn zoom-bar [current-layer-extent current-layer create-share-link time-slider?]
  (r/with-let [minZoom      (r/atom 0)
               maxZoom      (r/atom 28)
               *zoom        (r/atom 10)
               select-zoom! (fn [zoom]
                              (reset! *zoom (max @minZoom
                                                 (min @maxZoom
                                                      zoom)))
                              (mb/set-zoom! @*zoom))]
    (let [[cur min max] (mb/get-zoom-info)]
      (reset! *zoom cur)
      (reset! minZoom min)
      (reset! maxZoom max))
    (mb/add-map-zoom-end! #(reset! *zoom %))
    [:div#zoom-bar {:style ($/combine $/tool $tool-bar {:bottom (if (and @!/mobile? time-slider?) "90px" "36px")})}
     (map-indexed (fn [i [icon hover-text on-click]]
                    ^{:key i} [tool-tip-wrapper
                               hover-text
                               :right
                               [tool-button icon on-click]])
                  [[:share
                    "Share current map"
                    #(set-message-box-content! {:title "Share Current Map"
                                                :body  [share-inner-modal create-share-link]
                                                :mode  :close})]
                   [:terrain
                    (str (ed-str @!/terrain?) " 3D terrain")
                    #(do
                       (swap! !/terrain? not)
                       (when @!/terrain? (h/show-help! :terrain))
                       (mb/toggle-dimensions! @!/terrain?)
                       (mb/ease-to! {:pitch (if @!/terrain? 45 0) :bearing 0}))]
                   [:my-location
                    "Center on my location"
                    #(some-> js/navigator .-geolocation (.getCurrentPosition mb/set-center-my-location!))]
                   ;; TODO move this action to the information panel
                   ;;  [:center-on-point
                   ;;   "Center on selected point"
                   ;;   #(mb/center-on-overlay!)]
                   [:extent
                    "Zoom to fit layer"
                    #(mb/zoom-to-extent! current-layer-extent current-layer)]
                   [:zoom-in
                    "Zoom in"
                    #(select-zoom! (inc @*zoom))]
                   [:zoom-out
                    "Zoom out"
                    #(select-zoom! (dec @*zoom))]])]))
