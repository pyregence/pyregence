(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                                  :refer [<! go]]
   [clojure.edn                                         :as edn]
   [pyregence.components.mapbox                         :as mb]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.nav-bar               :refer [side-nav-bar-and-page]]
   [pyregence.state                                     :as !]
   [pyregence.utils.async-utils :as u-async]
   [pyregence.utils.browser-utils :as u-browser]
   [reagent.core :as r]))

(def psps-orgs (r/atom nil))

(defn root-component
  [{:keys [user-role]}]
  (let [update-fn (fn [& _]
                    (-> js/window (.scrollTo 0 0))
                    (reset! !/mobile? (> 800.0 (.-innerWidth js/window)))
                    (js/setTimeout mb/resize-map! 50))]
    (-> js/window (.addEventListener "touchend" update-fn))
    (-> js/window (.addEventListener "resize"   update-fn))
    (update-fn)
    (go
      ;; TODO This reset! and fetch logic is all to tell if the nav bar should
      ;; fetch the psps zones, and do it in roughly the same way the other
      ;; components.nav-bar are, and so it duplicates the logic in the
      ;; initialize function of near-term-forecasts... which isn't great, but
      ;; it's not clear how to share logic, or even if the original way is a good
      ;; one or just bi-product of growing complexity and needs re-wiring.
      (reset! psps-orgs

                (into #{}
                      (edn/read-string (:body (<! (u-async/call-clj-async! "get-psps-organizations"))))))
      (reset! !/user-orgs-list (edn/read-string
                                (:body
                                 (<! (u-async/call-clj-async!
                                      (if (#{"super_admin" "account_manager"} user-role)
                                        "get-all-organizations"
                                        "get-current-user-organization"))))))))
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
               :user-role          user-role
               :psps-organizations @psps-orgs
               :user-orgs-list     @!/user-orgs-list}]
     [side-nav-bar-and-page m]]))
