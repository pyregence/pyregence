(ns pyregence.components.map-controls.zone-search
  (:require [cljs.core.async.interop :refer-macros [<p!]]
            [clojure.core.async      :refer [<! go]]
            [clojure.string          :as str]
            [reagent.core            :as r]
            [pyregence.config        :as c]
            [pyregence.state         :as !]
            [pyregence.components.mapbox :as mb]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; Only these orgs have zones shapefiles published in GeoServer currently.
;; Remove this guard once all orgs have zones data.
(def orgs-with-zones #{"srp" "cowlitz" "tep"})

(defn has-zones-data?
  "Returns true if the current user belongs to an org with zones data in GeoServer."
  []
  (some #(orgs-with-zones (:org-unique-id %)) @!/user-psps-orgs-list))

(defn- zones-layer-name
  "Returns the WFS TYPENAME for a utility company's zones layer.
   e.g. \"psps-static_srp:srp-zones\""
  [org-unique-id]
  (str "psps-static_" org-unique-id ":" org-unique-id "-zones"))

(defn- fetch-zones-geojson!
  "Fetches the zones GeoJSON from GeoServer via WFS. Returns a core.async
   channel containing the parsed GeoJSON JS object (or nil on error)."
  [org-unique-id credentials]
  (go
    (try
      (let [url  (c/wfs-layer-url (zones-layer-name org-unique-id) :psps)
            resp (<p! (js/fetch url #js {:headers #js {:authorization (str "Basic " (js/window.btoa credentials))}}))
            json (<p! (.json resp))]
        json)
      (catch js/Error e
        (js/console.error "Failed to fetch zones GeoJSON:" (.-message e))
        nil))))

(defn- feature-bbox
  "Computes [minx miny maxx maxy] from a GeoJSON feature's geometry coordinates."
  [feature]
  (let [coords (-> feature (aget "geometry") (aget "coordinates"))
        flatten-coords (fn flatten-coords [c]
                         (if (number? (aget c 0))
                           [c]
                           (mapcat flatten-coords (array-seq c))))
        all-points (flatten-coords coords)
        lngs       (map #(aget % 0) all-points)
        lats       (map #(aget % 1) all-points)]
    [(apply min lngs) (apply min lats) (apply max lngs) (apply max lats)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn zone-search
  "A search component that fetches zone features for the user's PSPS organization
   and allows searching/selecting a zone to fly the map to its extent."
  []
  (let [*features-by-fid (r/atom {})
        *zone-options    (r/atom [])
        *search-text     (r/atom "")
        *selected-fid    (r/atom nil)
        *loading?        (r/atom false)
        *error           (r/atom false)
        load-zones!      (fn []
                           (when-let [org (first @!/user-psps-orgs-list)]
                             (let [{:keys [org-unique-id geoserver-credentials]} org]
                               (when (and org-unique-id
                                          geoserver-credentials
                                          (orgs-with-zones org-unique-id))
                                 (reset! *loading? true)
                                 (reset! *error nil)
                                 (go
                                   (let [geojson (<! (fetch-zones-geojson! org-unique-id geoserver-credentials))]
                                     (if geojson
                                       (let [features    (array-seq (aget geojson "features"))
                                             by-fid      (reduce (fn [acc f]
                                                                   (let [fid (str (aget f "properties" "fid"))]
                                                                     (assoc acc fid f)))
                                                                 {}
                                                                 features)
                                             sorted-fids (sort (keys by-fid))]
                                         (reset! *features-by-fid by-fid)
                                         (reset! *zone-options sorted-fids)
                                         (reset! *loading? false))
                                       (do
                                         (reset! *error "Failed to load zones")
                                         (reset! *loading? false)))))))))]
    (r/create-class
     {:component-did-mount
      (fn [_] (load-zones!))

      :reagent-render
      (fn []
        (let [search-text  @*search-text
              zone-options @*zone-options
              filtered     (if (str/blank? search-text)
                             zone-options
                             (filter #(str/includes?
                                       (str/lower-case %)
                                       (str/lower-case search-text))
                                     zone-options))]
          [:div
           [:label {:style {:font-weight "bold" :font-size "0.85rem"}} "Zone Search"]
           (cond
             @*loading?
             [:div {:style {:padding "0.5rem 0" :font-size "0.8rem" :color "#666"}}
              "Loading zones..."]

             @*error
             [:div {:style {:padding "0.5rem 0" :font-size "0.8rem" :color "red"}}
              (str "Error: " @*error)]

             :else
             [:<>
              [:input {:type        "text"
                       :placeholder "Search zones..."
                       :value       search-text
                       :on-change   #(reset! *search-text (.. % -target -value))
                       :style       {:width         "100%"
                                     :padding       "0.35rem 0.5rem"
                                     :margin-top    "0.4rem"
                                     :border        "1px solid #ccc"
                                     :border-radius "4px"
                                     :font-size     "0.85rem"
                                     :box-sizing    "border-box"}}]
              [:select {:value     (or @*selected-fid "")
                        :on-change (fn [e]
                                     (let [fid (.. e -target -value)]
                                       (reset! *selected-fid fid)
                                       (reset! *search-text "")))
                        :style     {:width         "100%"
                                    :padding       "0.35rem"
                                    :margin-top    "0.3rem"
                                    :border        "1px solid #ccc"
                                    :border-radius "4px"
                                    :font-size     "0.85rem"
                                    :box-sizing    "border-box"}}
               [:option {:value ""} (str "Select a zone (" (count filtered) ")")]
               (for [fid filtered]
                 ^{:key fid}
                 [:option {:value fid} fid])]
              [:button {:on-click (fn []
                                   (when-let [feature (get @*features-by-fid @*selected-fid)]
                                     (mb/fit-bounds! (feature-bbox feature))))
                        :disabled (str/blank? @*selected-fid)
                        :style    {:width         "100%"
                                   :padding       "0.4rem"
                                   :margin-top    "0.3rem"
                                   :border        "1px solid #ccc"
                                   :border-radius "4px"
                                   :font-size     "0.85rem"
                                   :cursor        (if (str/blank? @*selected-fid) "not-allowed" "pointer")
                                   :background    (if (str/blank? @*selected-fid) "#eee" "#4a90d9")
                                   :color         (if (str/blank? @*selected-fid) "#999" "#fff")}}
               "Go to zone"]])]))})))
