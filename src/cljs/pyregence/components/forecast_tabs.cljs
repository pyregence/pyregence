(ns pyregence.components.forecast-tabs
  (:require
   [pyregence.components.common    :refer [tool-tip-wrapper]]
   [pyregence.components.messaging :refer [toast-message!]]
   [pyregence.state                :as !]))

(defn- $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts."
  [{:keys [capabilities current-forecast select-forecast! user-orgs-list]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[key {:keys [allowed-orgs hover-text opt-label]}]]
           (when (or (nil? allowed-orgs)                        ; Tab isn't organization-specific, so show it
                     (some (fn [{org-unique-id :org-unique-id}] ; If tab **is** organization-specific
                             (allowed-orgs org-unique-id))      ; the user must be an admin or member of one of the allowed orgs
                           user-orgs-list))
             ^{:key key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= current-forecast key))
                       :on-click
                       (fn []
                         (when (= key :active-fire)
                           (when (and
                                  (zero? @!/active-fire-count)
                                  (zero? @!/active-fire-tab-click-count))
                             (toast-message! "No active fires."))
                           (swap! !/active-fire-tab-click-count inc))
                         (select-forecast! key))}
               opt-label]]))
         capabilities))])
