(ns pyregence.components.forecast-tabs
  (:require [pyregence.components.common :refer [tool-tip-wrapper]]
            [pyregence.utils.layer-utils :as u-layer]
            [pyregence.state             :as !]))

(defn $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts"
  []
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[key {:keys [opt-label hover-text allowed-org]}]]
           (when (or (nil? allowed-org)
                     (some (fn [{org-name :opt-label}]
                             (= org-name allowed-org))
                           @!/user-org-list))

             ^{:key key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= @!/*forecast key))
                       :on-click #(u-layer/select-forecast! key)}
               opt-label]])
           )

         @!/capabilities))])
