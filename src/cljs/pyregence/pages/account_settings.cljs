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
;; Functions copied and state logic copied from admin.cljs
;; TODO these should be shared with admin.cljs unless admin is going away.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce ^{:doc "Vector of the user's organizations."}
 *orgs (r/atom []))

;;TODO use pending-get-organizations or remove it.
(defonce ^{:doc "The pending state of a organization list request."}
 pending-get-organizations? (r/atom false))

;; mostly copied from admin, some small alterations, didn't need to set state in the same way...
(defn- get-organizations [user-role]
  (reset! pending-get-organizations? true)
  (go
    ;;TODO double check the security aspect of this... is get-all-organizations
    ;; some how more open then it should be?
    (let [api-route (if (= user-role "super_admin")
                      "get-all-organizations"
                      "get-current-user-organization")
          response  (<! (u-async/call-clj-async! api-route))]
      (reset! *orgs (if (:success response)
                      (->> (:body response)
                           (edn/read-string))
                      []))
      (reset! pending-get-organizations? false))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role user-email]}]
  ;; TODO it feels awkward to just have this get-organizations api
  ;; call floating at the top, but how else to organize it?
  ;; TODO get-organizations could happen just when they click
  ;;organization settings, but then that would be slower...
  (get-organizations user-role)
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
                     :organizations (mapv :org-name @*orgs)
                     :user-role     user-role}]
      (case (last @selected-log)
        :account-settings
        [body/main {:password-set-date "1/2/2020"
                    :email-address     user-email
                    :role-type         user-role
                    :first-name        "drew"
                    :last-name         "verlee"}]
        [:p "TODO"])])])
