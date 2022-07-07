(ns pyregence.components.forecast-tabs
  (:require [pyregence.components.common        :refer [tool-tip-wrapper]]))

(defn- $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts"
  [{:keys [capabilities current-forecast org-list-where-admin select-forecast!]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[key {:keys [allowed-org hover-text opt-label]}]]
           (when (or (nil? allowed-org)
                     (some (fn [{org-name :opt-label}]
                             (= org-name allowed-org))
                           org-list-where-admin))
             ^{:key key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= current-forecast key))
                       :on-click #(select-forecast! key)}
               opt-label]]))
         capabilities))])
