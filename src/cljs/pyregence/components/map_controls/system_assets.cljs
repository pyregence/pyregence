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
        ID                      "System Assets"]
    (go
      (reset! orgs-with-system-assets
              (let [{:keys [body success]}
                    (<! (u-async/call-clj-async! "get-orgs-with-system-assets"))]
                (when success (edn/read-string body)))))
    #(when-let [orgs-with-system-assets (seq @orgs-with-system-assets)]
       [u/collapsible-panel-section
        ID
        (let [geoserver "psps"
              system-assets-info
              (for [{:keys [org_unique_id org_name] :as o} orgs-with-system-assets
                    {:keys [device color] :as d}           [{:device "fuse" :color "red"}
                                                            {:device "substation" :color "blue"}
                                                            {:device "recloser" :color "green"}
                                                            {:device "breaker" :color "orange"}]
                    :let                                   [source-layer (str org_unique_id "-devices")
                                                            layer-name (str geoserver "-static_" org_unique_id "%3A" source-layer)
                                                            id (str org_unique_id "-" device)]]
                (merge o d
                       {:id           id
                        :opt-label    (str (str/capitalize device) "s " "(" org_name ")")
                        :z-index      110
                        :filter-set   #{org_unique_id}
                        :source-layer source-layer
                        :layer-name   layer-name
                        :new-sources  {id (mb/mvt-source layer-name geoserver)}
                        :new-layers   [{:id           id
                                        :source       id
                                        :source-layer source-layer
                                        :filter       ["==" ["get" "DVC_TYPE"] device]
                                        :type         "circle"
                                        :paint        {:circle-radius  6
                                                       :circle-color   color
                                                       :circle-opacity 1}}]}))]
          (->> system-assets-info
               (run! (fn [{:keys [new-sources new-layers id source-layer]}]
                       (mb/update-style! (mb/get-style) :new-sources new-sources :new-layers new-layers)
                       (mb/set-visible-by-title! id false)
                       (mb/add-feature-highlight! id id
                                                  :click-fn
                                                  (fn [feature lnglat]
                                                    (mb/init-popup! ID lnglat
                                                                    [popup
                                                                     (let [{:keys [DVC_ID DVC_TYPE]}
                                                                           (js->clj (gobj/get feature "properties")
                                                                                    :keywordize-keys true)]
                                                                       {:header  DVC_ID
                                                                        :options [{:label "Device Type" :value DVC_TYPE}]})]
                                                                    {:width "200px"}))
                                                  :source-layer source-layer))))
          [:<>
           [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
            [:label ID]
            (for [{:keys [id opt-label] :as m} system-assets-info]
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
                        (swap! !/*optional-layers assoc id m)
                        (reset! !/most-recent-optional-layer m))
                      (reset! !/most-recent-optional-layer {}))))}])]])])))
