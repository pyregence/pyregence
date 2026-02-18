(ns pyregence.pages.account-settings
  (:require
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.nav-bar               :refer [side-nav-bar-and-page]]
   [pyregence.state                                     :as !]
   [pyregence.utils.browser-utils                       :as u-browser]
   [pyregence.utils.misc-utils                          :refer [on-mount-defaults]]))

(defn root-component
  [_]
  (on-mount-defaults)
  (fn [{:keys [user-role] :as m}]
    [:div
     {:style {:height         "100vh"
              :margin-bottom  "40px"
              :display        "flex"
              :flex-direction "column"
              :font-family    "Roboto"
              :padding-bottom "60px"}}
     [nav-bar {:logged-in?         true
               :mobile?            @!/mobile?
               :on-forecast-select #(u-browser/jump-to-url! (str "/?forecast=" (name %)))
               :user-role          user-role}]
     [side-nav-bar-and-page m]]))
