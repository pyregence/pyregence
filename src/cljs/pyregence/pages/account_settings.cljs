(ns pyregence.pages.account-settings
  (:require
   [pyregence.components.settings.body    :as body]
   [pyregence.components.settings.nav-bar :as nav-bar]
   [pyregence.styles                      :as $]
   [reagent.core :as r]))

(defn root-component
  "The root component of the /account-settings page."
  []
  [:div {:style {:height         "100%"
                 :display        "flex"
                 :flex-direction "column"
                 :font-family    "Roboto"}}
   ;; TODO replace with actual upper nav bar
   [:nav  {:style {:display         "flex"
                   :justify-content "center"
                   :align-items     "center"
                   :width           "100%"
                   :height          "33px"
                   :background      ($/color-picker :yellow)}} "mock nav"]
   (r/with-let [selected-log (r/atom [:account-settings])]
     [:div {:style {:display        "flex"
                    :flex-direction "row"
                    :height         "100%"
                    :background     ($/color-picker :lighter-gray)}}
      [nav-bar/main {:organizations ["org1" "org2"]
                     :selected-log  selected-log}]
      (when (= :account-settings (last @selected-log))
        [body/main {:password-set-date "1/2/2020"
                    :email-address     "drew.verlee@gmail.com"
                    :role-type         "fire fighter"
                    :first-name        "drew"
                    :last-name         "verlee"}])])])
