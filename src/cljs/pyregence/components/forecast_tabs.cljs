(ns pyregence.components.forecast-tabs
  (:require [pyregence.components.common :refer [tool-tip-wrapper]]))

(defn- $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts"
  [{:keys [capabilities current-forecast select-forecast! user-org-list]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[key {:keys [allowed-org hover-text opt-label]}]]
           (when (or (nil? allowed-org)                ; Tab isn't organization-specific
                     (some (fn [{org-name :opt-label}] ; Tab **is** organization-specific
                             (= org-name allowed-org)) ; and the user is an admin or member of that org
                           user-org-list))             ; (the organization name is in their org-list)
             ^{:key key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= current-forecast key))
                       :on-click #(select-forecast! key)}
               opt-label]]))
         capabilities))])
