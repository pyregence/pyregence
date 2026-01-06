(ns pyregence.components.map-controls.system-assets
  (:require
   [clojure.core.async                               :refer [<! go]]
   [clojure.edn                                      :as edn]
   [clojure.string                                   :as str]
   [goog.object                                      :as gobj]
   [pyregence.components.map-controls.utils          :as u]
   [pyregence.components.mapbox                      :as mb]
   [pyregence.components.popups                      :refer [popup]]
   [pyregence.state                                  :as !]
   [pyregence.utils.async-utils                      :as u-async]
   [reagent.core                                     :as r]))

(defn panel-section
  []
  (let [orgs-with-system-assets (r/atom nil)
        device-info [{:device "fuse"       :color "red"}
                     {:device "substation" :color "blue"}
                     {:device "recloser"   :color "green"}
                     {:device "breaker"    :color "orange"}]]
    (r/create-class
     {:component-did-mount
      #(go
         (when-let [orgs-with-system-assets
                    (seq
                     (reset! orgs-with-system-assets
                             (let [{:keys [body success]}
                                   (<! (u-async/call-clj-async! "get-orgs-with-system-assets"))]
                               (when success (edn/read-string body)))))]
           (let [geoserver "psps"
                 info (for [{:keys [org_unique_id] :as o} orgs-with-system-assets
                            {:keys [device] :as d}  device-info]
                        (merge o d {:id (str org_unique_id "-" device) :source-layer (str org_unique_id "-devices")}))
                 new-layers (for [{:keys [id device color source-layer]} info]
                              {:id           id
                               :source       id
                               :source-layer source-layer
                               :filter       ["==" ["get" "DVC_TYPE"] device]
                               :type         "circle"
                               :paint        {:circle-radius  6
                                              :circle-color   color
                                              :circle-opacity 1}})
                 new-sources (reduce
                              (fn [new-sources {:keys [id org_unique_id source-layer]}]
                                (assoc new-sources id (mb/mvt-source (str geoserver "-static_" org_unique_id "%3A" source-layer) geoserver)))
                              {}
                              info)]
             (mb/update-style! (mb/get-style) :new-sources new-sources :new-layers new-layers)
             (doseq [{:keys [id source-layer]} info]
               (mb/set-visible-by-title! id false)
               (mb/add-feature-highlight! id id
                                          :click-fn
                                          (fn [feature lnglat]
                                            (mb/init-popup! "system-assets" lnglat
                                                            [popup
                                                             (let [{:keys [DVC_ID DVC_TYPE]}
                                                                   (js->clj (gobj/get feature "properties")
                                                                            :keywordize-keys true)]
                                                               {:header  DVC_ID
                                                                :options [{:label "Device Type" :value DVC_TYPE}]})]
                                                            {:width "200px"}))
                                          :source-layer source-layer)))))

      :reagent-render
      #(when-let [orgs-with-system-assets (seq @orgs-with-system-assets)]
         [u/collapsible-panel-section
          "System Assets"
          [:<>
           [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
            [:label "System Assets"]
            (for [{:keys [org_unique_id org_name]} orgs-with-system-assets
                  {:keys [device]} device-info
                  :let                        [id (str org_unique_id "-" device)
                                               opt-label (str (str/capitalize device) "s " "(" org_name ")")
                                               ;;TODO what needs to be in this layer?
                                               layer {:device       device
                                                      :id           id
                                                      :z-index      110
                                                      :filter-set   #{org_unique_id}}]]
              ^{:key id}
              [u/option
               {:id    id
                :label opt-label
                :on-change
                (fn [show?]
                  (let [show? (swap! show? not)]
                    (mb/set-visible-by-title! id show?)
                    (if show?
                      (do
                        (swap! !/*optional-layers assoc id layer)
                        (reset! !/most-recent-optional-layer layer))
                      (reset! !/most-recent-optional-layer {}))))}])]]])})))
