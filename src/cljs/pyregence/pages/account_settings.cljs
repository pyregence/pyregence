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
  [{:keys [user-role user-email user-name]}]
  ;; TODO it feels awkward to just have this get-organizations api
  ;; call floating at the top, but how else to organize it?
  ;; TODO get-organizations could happen just when they click
  ;;organization settings, but then that would be slower...
  (r/with-let [orgs (r/atom nil)
               selected-log (r/atom [:account-settings])]
    ;; TODO this conditional check on @orgs prevents repeating calls to the backend
    ;; But it might not make sense if we need to update orgs at other points. Consider other ways.
    (when-not @orgs
      (go
        ;;TODO double check the security aspect of this... is get-all-organizations
        ;; some how more open then it should be?
        (let [api-route (if (= user-role "super_admin")
                          "get-all-organizations"
                          "get-current-user-organization")
              response  (<! (u-async/call-clj-async! api-route))]
          (reset! orgs (if (:success response)
                         (->> (:body response)
                              (edn/read-string))
                         [])))))
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
     (let [tabs     (nav-bar/tab-data->tabs
                      {:selected-log  selected-log
                       :organizations (mapv :org-name @orgs)
                       :user-role     user-role})
           selected->tab-id (fn [selected]
                              (:id (first ((group-by :selected? tabs) selected))))
           selected (-> @selected-log last)
           selected-page (selected->tab-id selected)]
       [:div {:style {:display        "flex"
                      :flex-direction "row"
                      :height         "100%"
                      :background     ($/color-picker :lighter-gray)}}
        [nav-bar/main tabs]
        (case selected-page
          :account-settings
          [body/main {:password-set-date "1/2/2020"
                      :email-address     user-email
                      :role-type         user-role
                      :user-name         user-name}]
          :organization-settings
          [:p selected]
          [:p "TODO"])])]))
