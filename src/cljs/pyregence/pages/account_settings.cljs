(ns pyregence.pages.account-settings
  (:require
   [pyregence.components.mapbox                         :as mb]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.nav-bar               :refer [side-nav-bar-and-page]]
   [pyregence.state                                     :as !]
   [pyregence.utils.browser-utils                       :as u-browser]))

(defn root-component
  [_]
  #(let [update-fn (fn [& _]
                    (-> js/window (.scrollTo 0 0))
                    (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                    (js/setTimeout mb/resize-map! 50))]
    (-> js/window (.addEventListener "touchend" update-fn))
    (-> js/window (.addEventListener "resize"   update-fn))
    (update-fn))
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
