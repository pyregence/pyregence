(ns pyregence.pages.account-settings
  (:require
   [cljs.reader :as edn]
   [clojure.core.async                                  :refer [<! go]]
   [clojure.string                                      :as str]
   [pyregence.components.nav-bar                        :refer [nav-bar]]
   [pyregence.components.settings.nav-bar               :refer [side-nav-bar-and-page]]
   [pyregence.state                                     :as !]
   [pyregence.utils.async-utils                         :as u-async]
   [pyregence.utils.browser-utils                       :as u-browser]
   [reagent.core                                        :as r]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-orgs!
  [user-role]
  (go
    (let [api-route (if (#{"super_admin" "account_manager"} user-role)
                      "get-all-organizations"
                      "get-current-user-organization")
          response  (<! (u-async/call-clj-async! api-route))]
      (if (:success response)
        (->> (:body response)
             (edn/read-string))
        {}))))

(defn orgs->org->id
  [orgs]
  (reduce
   (fn [org-id->org {:keys [org-id org-name email-domains auto-accept? auto-add?] :as org}]
     (assoc org-id->org org-id
            (assoc org
                   :unsaved-auto-accept? auto-accept?
                   :unsaved-auto-add?    auto-add?
                   :unsaved-org-name     org-name
                   ;; NOTE this mapping is used to keep track of the email
                   :og-email->email (reduce
                                     (fn [m e]
                                       (assoc m e {:email e :unsaved-email e}))
                                     {}
                                     (->>
                                      (str/split email-domains #",")
                                      (map #(str/replace % "@" "")))))))
   {}
   orgs))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /account-settings page."
  [{:keys [user-role] :as m}]
  ;;TODO move org-id->org into two ratoms, one for fetching the organizations from the side nav bar
  ;;and another for fetching the org, in the organization-settings component info once it's been selected from the side.
  (let [org-id->org (r/atom nil)]
    (r/create-class
     {:display-name "account-settings"
      :component-did-mount
      #(go
         (let [update-fn (fn [& _]
                           (-> js/window (.scrollTo 0 0))
                           (reset! !/mobile? (> 800.0 (.-innerWidth js/window))))]
           (-> js/window (.addEventListener "touchend" update-fn))
           (-> js/window (.addEventListener "resize"   update-fn))
           (reset! org-id->org (orgs->org->id (<! (get-orgs! user-role))))
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
         [side-nav-bar-and-page
          (assoc m
                 :organizations (vals @org-id->org)
                 :org-id->org   org-id->org)]])})))
