(ns pyregence.pages.account-settings
  (:require
   [clojure.core.async                    :refer [<! go]]
   [clojure.edn                           :as edn]
   [pyregence.components.settings.body    :as body]
   [pyregence.components.settings.nav-bar :as nav-bar]
   [pyregence.styles                      :as $]
   [pyregence.utils.async-utils           :as u-async]
   [reagent.core                          :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role] :as m}]
  (let [orgs (r/atom nil)]
    (r/create-class
     {:display-name "account-sttings"
      :component-did-mount
      #(go
         (let [api-route (if (= user-role "super_admin")
                           "get-all-organizations"
                           "get-current-user-organization")
               response  (<! (u-async/call-clj-async! api-route))]
           (println "reset")
           (println
            (reset! orgs (if (:success response)
                           (->> (:body response)
                                (edn/read-string))
                           [])))))
      :reagent-render
      (fn [{:keys [user-role user-email user-name] :as m}]
        (println @orgs)
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
            [nav-bar/main {:selected-log  selected-log
                           :organizations (mapv :org-name @orgs)
                           :user-role     user-role}]
            (case (last @selected-log)
              :account-settings
             ;;TODO consider just passing args through, not renaming or re-mapping.
              [body/main {:password-set-date "1/2/2020"
                          :email-address     user-email
                          :role-type         user-role
                          :user-name         user-name}]
              [:p "TODO"])])])})))
