(ns pyregence.components.map-controls.camera-tool
  (:require [clojure.core.async                    :refer [take! go <!]]
            [clojure.edn                           :as edn]
            [herb.core                             :refer [<class]]
            [pyregence.components.common           :refer [tool-tip-wrapper]]
            [pyregence.components.help             :as h]
            [pyregence.components.mapbox           :as mb]
            [pyregence.components.resizable-window :refer [resizable-window]]
            [pyregence.components.svg-icons        :as svg]
            [pyregence.state                       :as !]
            [pyregence.styles                      :as $]
            [pyregence.utils                       :as u]
            [pyregence.utils.async-utils           :as u-async]
            [pyregence.utils.number-utils          :as u-num]
            [pyregence.utils.time-utils            :as u-time]
            [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- get-camera-image-chan [active-camera]
  (go
    (->> (u-async/call-clj-async! "get-current-image" :post-blob (:name active-camera))
         (<!)
         (:body)
         (js/URL.createObjectURL))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $awf-logo-style []
  {:height    "auto"
   :left      "2rem"
   :min-width "100px"
   :position  "absolute"
   :top       "2rem"
   :width     "10%"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn camera-tool [parent-box close-fn!]
  (r/with-let [active-camera (r/atom nil)
               camera-age    (r/atom 0)
               image-src     (r/atom nil)
               exit-chan     (r/atom nil)
               zoom-camera   (fn []
                               (let [{:keys [longitude latitude tilt pan]} @active-camera]
                                 (reset! !/terrain? true)
                                 (h/show-help! :terrain)
                                 (mb/toggle-dimensions! true)
                                 (mb/fly-to! {:center  [longitude latitude]
                                              :zoom    15
                                              :bearing pan
                                              :pitch   (min (+ 90 tilt) 85)}) 400))
               reset-view    (fn []
                               (let [{:keys [longitude latitude]} @active-camera]
                                 (reset! !/terrain? false)
                                 (mb/toggle-dimensions! false)
                                 (mb/fly-to! {:center [longitude latitude]
                                              :zoom   6})))
               on-click      (fn [features]
                               (go
                                 (when-let [new-camera (js->clj (aget features "properties") :keywordize-keys true)]
                                   (u-async/stop-refresh! @exit-chan)
                                   (reset! active-camera new-camera)
                                   (reset! camera-age 0)
                                   (reset! image-src nil)
                                   (let [image-chan  (get-camera-image-chan @active-camera)]
                                     (reset! camera-age (-> (:update-time @active-camera)
                                                            (u-time/camera-time->js-date)
                                                            (u-time/get-time-difference)
                                                            (u-time/ms->hr)))
                                     (when (> 4 @camera-age)
                                       (reset! image-src (<! image-chan))
                                       (reset! exit-chan
                                               (u-async/refresh-on-interval! #(go (reset! image-src (<! (get-camera-image-chan @active-camera))))
                                                                       60000)))))))
               ;; TODO, this form is sloppy.  Maybe return some value to store or convert to form 3 component.
               _             (take! (mb/create-camera-layer! "fire-cameras")
                                    #(mb/add-feature-highlight! "fire-cameras" "fire-cameras" :click-fn on-click))]
    [:div#wildfire-camera-tool
     [resizable-window
      parent-box
      290
      460
      "Wildfire Camera Tool"
      close-fn!
      (fn [_ _]
        (cond
          (nil? @active-camera)
          [:div {:style {:padding "1.2em"}}
           "Click on a camera to view the most recent image. Powered by "
           [:a {:href   "http://www.alertwildfire.org/"
                :ref    "noreferrer noopener"
                :target "_blank"}
            "Alert Wildfire"] "."]

          (>= @camera-age 4)
          [:div {:style {:padding "1.2em"}}
           [:p (str "This camera has not been refreshed for " (u-num/to-precision 1 @camera-age) " hours. Please try again later.")]
           [:p "Click"
            [:a {:href   (str "https://www.alertwildfire.org/region/?camera=" (:name @active-camera))
                 :ref    "noreferrer noopener"
                 :target "_blank"}
                " here "]
            "for more information about the " (:name @active-camera) " camera."]]

          @image-src
          [:div
           [:div {:style {:display         "flex"
                          :justify-content "center"
                          :position        "absolute"
                          :top             "2rem"
                          :width           "100%"}}
            [:label (str "Camera: " (:name @active-camera))]]
           [:img {:src   "images/awf_logo.png"
                  :style ($/combine $awf-logo-style)}]
           (when @!/terrain?
             [tool-tip-wrapper
              "Zoom Out to 2D"
              :left
              [:button {:class    (<class $/p-themed-button)
                        :on-click reset-view
                        :style    {:bottom   "1.25rem"
                                   :padding  "2px"
                                   :position "absolute"
                                   :left     "1rem"}}
               [:div {:style {:height "32px"
                              :width  "32px"}}
                [svg/return]]]])
           [tool-tip-wrapper
            "Zoom Map to Camera"
            :right
            [:button {:class    (<class $/p-themed-button)
                      :on-click zoom-camera
                      :style    {:bottom   "1.25rem"
                                 :padding  "2px"
                                 :position "absolute"
                                 :right    "1rem"}}
             [:div {:style {:height "32px"
                            :width  "32px"}}
              [svg/binoculars]]]]
           [:img {:src   @image-src
                  :style {:height "auto" :width "100%"}}]]

          :else
          [:div {:style {:padding "1.2em"}}
           (str "Loading camera " (:name @active-camera) "...")]))]]
    (finally
      (u-async/stop-refresh! @exit-chan)
      (mb/remove-layer! "fire-cameras")
      (mb/clear-highlight! "fire-cameras" :selected))))
