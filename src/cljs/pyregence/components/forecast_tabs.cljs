(ns pyregence.components.forecast-tabs
  (:require [pyregence.components.common :refer [tool-tip-wrapper]]))

(defn- $forecast-label [selected?]
  (merge
   {:cursor "pointer"
    :margin "0 1rem 0 1rem"}
   (when selected? {:color "white"})))

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts.
   Allows super_admins to see all tabs."
  [{:keys [capabilities current-forecast select-forecast! user-orgs-list user-role]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[key {:keys [allowed-orgs hover-text opt-label]}]]
           (when (or (= user-role "super_admin")                ; Show everything for super_admins
                     (nil? allowed-orgs)                        ; Tab isn't organization-specific, so show it
                     (some (fn [{org-unique-id :org-unique-id}] ; If tab **is** organization-specific
                             (allowed-orgs org-unique-id))      ; the user must be an organization_admin or organization_member of one of the allowed orgs
                           user-orgs-list))
             ^{:key key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= current-forecast key))
                       :on-click #(select-forecast! key)}
               opt-label]]))
         capabilities))])
