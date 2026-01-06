(ns pyregence.components.map-controls.system-assets
  (:require
   [clojure.core.async                      :refer [<! go]]
   [clojure.edn                             :as edn]
   [clojure.string                          :as str]
   [goog.object                             :as gobj]
   [pyregence.components.map-controls.utils :as u]
   [pyregence.components.mapbox             :as mb]
   [pyregence.components.popups             :refer [popup]]
   [pyregence.state                         :as !]
   [pyregence.utils.async-utils             :as u-async]
   [reagent.core                            :as r]))

(defn panel-section
  []
  (let [orgs-with-system-assets (r/atom nil)
        device-info             [{:device "fuse"       :color "red"    :hover-color "#9C2007" :selected-color "#F87C63"}
                                 {:device "substation" :color "blue"   :hover-color "#072F9C" :selected-color "#638BF8"}
                                 {:device "recloser"   :color "green"  :hover-color "#177005" :selected-color "#7CF863"}
                                 {:device "breaker"    :color "orange" :hover-color "#9C5907" :selected-color "#FACA8F"}]]
    (go
      (reset! orgs-with-system-assets
              (let [{:keys [body success]}
                    (<! (u-async/call-clj-async! "get-orgs-with-system-assets"))]
                (when success (edn/read-string body)))))
    #(when-let [orgs-with-system-assets (seq @orgs-with-system-assets)]
       [u/collapsible-panel-section
        "System Assets"
        [:<>
         [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
          [:label "System Assets"]
          (for [{:keys [org_unique_id org_name]} orgs-with-system-assets
                {:keys [device
                        color
                        hover-color
                        selected-color]}         device-info
                :let                             [id           (str org_unique_id "-" device)
                                                  source-layer (str org_unique_id "-devices")
                                                  opt-label    (str (str/capitalize device) "s " "(" org_name ")")
                                                  layer-name   (str "psps-static_" org_unique_id "%3A" source-layer)
                                                  layer        {:id         id
                                                                :z-index    110
                                                                :filter-set #{org_unique_id}}]]
            ^{:key id}
            [u/option
             {:id    id
              :label opt-label
              :on-change
              (fn [show?]
                (let [show? (swap! show? not)]
                  (when show?
                    (when-not (js-invoke @mb/the-map "getSource" id)
                      (js-invoke @mb/the-map "addSource"  id (clj->js (mb/mvt-source layer-name "psps"))))
                    (when-not (js-invoke @mb/the-map "getLayer" id)
                      ;;TODO consider using a different :source then :id because they might clash down the line.
                      (js-invoke @mb/the-map "addLayer" (clj->js {:id           id
                                                                  :source       id
                                                                  :source-layer source-layer
                                                                  :filter       ["==" ["get" "DVC_TYPE"] device]
                                                                  :type         "circle"
                                                                  :paint        {:circle-radius  6
                                                                                 :circle-color   (mb/on-selected selected-color hover-color color)
                                                                                 :circle-opacity (mb/on-hover 0.4 1)}}))
                      (mb/add-feature-highlight! id id :click-fn (fn [feature lnglat]
                                                                   (mb/init-popup! "system-assets" lnglat
                                                                                   [popup
                                                                                    (let [{:keys [DVC_ID DVC_TYPE]}
                                                                                          (js->clj (gobj/get feature "properties")
                                                                                                   :keywordize-keys true)]
                                                                                      {:header  DVC_ID
                                                                                       :options [{:label "Device Type" :value DVC_TYPE}]})]
                                                                                   {:width "200px"})) :source-layer source-layer)))
                  (mb/set-visible-by-title! id show?)
                  (if show?
                    (do
                      (swap! !/*optional-layers assoc id layer)
                      (reset! !/most-recent-optional-layer layer))
                    (do
                      (swap! !/*optional-layers dissoc id)
                      (reset! !/most-recent-optional-layer {})))))}])]]])))
