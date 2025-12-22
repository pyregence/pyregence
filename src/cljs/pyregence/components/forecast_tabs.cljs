(ns pyregence.components.forecast-tabs
  (:require
   [herb.core                   :refer [<class]]
   [pyregence.components.common :refer [tool-tip-wrapper]]
   [pyregence.styles             :as $]))

(defn- $forecast-label [selected? mobile?]
  {:background-color (when selected? ($/color-picker :primary-main-orange))
   :color            ($/color-picker :black)
   :cursor           "pointer"
   :font-family      "Roboto"
   :font-weight      400
   :font-size        "16px"
   :padding          (if mobile?
                       "16px 12px"
                       "16px 24px")})

(defn forecast-tabs
  "Declares a component that displayes interactive tabs for selecting distinct forecasts.
   Allows super_admins to see all tabs."
  [{:keys [capabilities current-forecast select-forecast! user-orgs-list user-role mobile?]}]
  [:div {:style {:display "flex" :padding ".25rem 0"}}
   (doall
    (map (fn [[forecast-key {:keys [allowed-orgs hover-text opt-label]}]]
           (when (or (= user-role "super_admin")                ; Show everything for super_admins
                     (nil? allowed-orgs)                        ; Tab isn't organization-specific, so show it
                     (some (fn [{org-unique-id :org-unique-id}] ; If tab **is** organization-specific
                             (allowed-orgs org-unique-id))      ; the user must be an organization_admin or organization_member of one of the allowed orgs
                           user-orgs-list))
             ^{:key forecast-key}
             [tool-tip-wrapper
              hover-text
              :top
              [:label {:style    ($forecast-label (= current-forecast forecast-key) mobile?)
                       :class    (<class $/p-add-hover)
                       :on-click #(select-forecast! forecast-key)}
               opt-label]]))
         capabilities))])
