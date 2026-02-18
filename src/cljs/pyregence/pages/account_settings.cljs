(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [go]]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.nav-bar               :refer [side-nav-bar-and-page]]
   [pyregence.state                                     :as !]
   [pyregence.utils.browser-utils                       :as u-browser]
   [reagent.core                                        :as r]))

(defn root-component
  "The root component of the /account-settings page."
  [m]
  (r/create-class
   {:display-name "account-settings"
    :component-did-mount
    #(go
       (let [update-fn (fn [& _]
                         (-> js/window (.scrollTo 0 0))
                         (reset! !/mobile? (> 800.0 (.-innerWidth js/window))))]
         (-> js/window (.addEventListener "touchend" update-fn))
         (-> js/window (.addEventListener "resize"   update-fn))
         (update-fn)))
    :reagent-render
    (fn [{:keys [user-role]}]
      [:div
       {:style {:height         "100vh"
                :margin-bottom  "40px"
                :display        "flex"
                :flex-direction "column"
                :font-family    "Roboto"
                  ;;NOTE this padding-bottom is to account for the header, there is probably a better way.
                :padding-bottom "60px"}}
       [nav-bar {:logged-in?         true
                 :mobile?            @!/mobile?
                 :on-forecast-select (fn [forecast]
                                       (u-browser/jump-to-url!
                                        (str "/?forecast=" (name forecast))))
                 :user-role          user-role}]
       [side-nav-bar-and-page m]])}))
