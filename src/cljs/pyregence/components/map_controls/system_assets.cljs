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
        device-info             [{:device "fuse" :color "red"}
                                 {:device "substation" :color "blue"}
                                 {:device "recloser" :color "green"}
                                 {:device "breaker" :color "orange"}]
        ->id                    (fn [org_unique_id device] (str org_unique_id "-" device))]
    (r/create-class
     {:component-did-mount
      #(go
         (when-let [orgs-with-system-assets
                    (seq
                     (reset! orgs-with-system-assets
                             (let [{:keys [body success]}
                                   (<! (u-async/call-clj-async! "get-orgs-with-system-assets"))]
                               (when success (edn/read-string body)))))]
           (doseq [{:keys [org_unique_id]} orgs-with-system-assets
                   {:keys [device color]}  device-info
                   :let                    [id (->id org_unique_id device)
                                            source-layer (str org_unique_id "-devices")
                                            layer-name (str "psps-static_" org_unique_id "%3A" source-layer)]]

             (when-not (.getSource @mb/the-map id)
               (.addSource @mb/the-map id (clj->js (mb/mvt-source layer-name "psps"))))

             (when-not (.getLayer @mb/the-map id)
               (.addLayer @mb/the-map (clj->js {:id           id
                                                :source       id
                                                :source-layer source-layer
                                                :filter       ["==" ["get" "DVC_TYPE"] device]
                                                :type         "circle"
                                                :paint        {:circle-radius 6 :circle-color color}})))

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
                                        :source-layer source-layer))))

      :reagent-render
      #(when-let [orgs-with-system-assets (seq @orgs-with-system-assets)]
         [u/collapsible-panel-section
          "System Assets"
          [:<>
           [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
            [:label "System Assets"]
            (for [{:keys [org_unique_id org_name]} orgs-with-system-assets
                  {:keys [device]}                 device-info
                  :let                             [id (->id org_unique_id device)
                                                    opt-label (str (str/capitalize device) "s " "(" org_name ")")
                                                    layer {:id         id
                                                      ;;TODO does it need opt-label...i bet not
                                                           :opt-label  opt-label
                                                           :z-index    110
                                                           :filter-set #{org_unique_id}}]]
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
