(ns pyregence.components.map-controls.tool-bar
  (:require [pyregence.state     :as !]
            [pyregence.styles    :as $]
            [pyregence.config    :as c]
            [pyregence.components.common      :refer [tool-tip-wrapper hs-str toggle-red-flag-layer! toggle-fire-history-layer!]]
            [pyregence.components.tool-button :refer [tool-button]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $tool-bar []
  {:display        "flex"
   :flex-direction "column"
   :right          "16px"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn tool-bar [set-show-info! user-id]
  [:div#tool-bar {:style ($/combine $/tool $tool-bar {:top "16px"})}
   (->> [(when-not @!/mobile?
           [:info
            (str (hs-str @!/show-info?) " point information")
            #(do (set-show-info! (not @!/show-info?))
                 (reset! !/show-match-drop? false)
                 (reset! !/show-camera? false))
            @!/show-info?])
         (when (and (c/feature-enabled? :match-drop) (number? user-id) (not @!/mobile?))
           [:flame
            (str (hs-str @!/show-match-drop?) " match drop tool")
            #(do (swap! !/show-match-drop? not)
                 (set-show-info! false)
                 (reset! !/show-camera? false))
            @!/show-match-drop?])
         (when-not @!/mobile?
           [:camera
            (str (hs-str @!/show-camera?) " cameras")
            #(do (swap! !/show-camera? not)
                 (set-show-info! false)
                 (reset! !/show-match-drop? false))
            @!/show-camera?])
         [:flag
          (str (hs-str @!/show-red-flag?) " red flag warnings")
          toggle-red-flag-layer!]
         (when (and (c/feature-enabled? :fire-history) (not @!/mobile?))
           [:clock
            (str (hs-str @!/show-fire-history?) " fire history")
            toggle-fire-history-layer!])
         [:legend
          (str (hs-str @!/show-legend?) " legend")
          #(swap! !/show-legend? not)
          false]]
        (remove nil?)
        (map-indexed (fn [i [icon hover-text on-click active?]]
                       ^{:key i} [tool-tip-wrapper
                                  hover-text
                                  :right
                                  [tool-button icon on-click active?]])))])
