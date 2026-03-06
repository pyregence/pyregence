;; TODO this shares logic with the add_user.cljs and delete_user.cljs consider combining them.
;; I think that's mostly a matter of sharing the confirmation pop up logic.
(ns pyregence.components.settings.update-user
  (:require
   [pyregence.components.buttons        :as buttons]
   [pyregence.components.settings.roles :as roles]
   [pyregence.styles                    :as $]
   [reagent.core                        :as r]))

(def selected-rows (r/atom nil))

;;TODO merge this with the other uses of confirm-modal
(defn confirm-modal
  [{:keys [on-click on-click-close-dialog checked organizations select-org-msg opt->display]}]
  (let [bc       ($/color-picker :neutral-light-gray)
        org-name (r/atom nil)]
    [:div {:style {:width          "fit-content"
                   :height         "fit-content"
                   :display        "flex"
                   :flex-direction "column"
                   :border-radius  "10px"
                   :padding        "20px"
                   :gap            "20px"}}
     [:header
      {:style {:border-bottom-color bc
               :border-bottom-width "3px"
               :border-bottom-style "solid"
               :padding-bottom      "20px"
               :font-weight         "bolder"
               :font-size           "1.5rem"}}
      (or select-org-msg  (str "Confirm Update Users: " (opt->display @checked) "."))]
     (when select-org-msg
       [buttons/drop-down {:options (sort organizations)
                           :checked org-name
                           :opt->display identity
                           :disabled? (nil? @org-name)}])
     [:div {:style {:display        "flex"
                    :flex-direction "column"
                    :gap            "10px"}}
      [:p "Users Before Update"]
      [:table {:style {:border          "1px solid #dddddd"
                       :border-collapse "separate"
                       :border-spacing  "0"
                       :border-radius   "6px"
                       :overflow        "hidden"}}
       (let [s  {:style {:border-bottom "1px solid #dddddd"
                         :text-align    "left"
                         :padding       "20px"}}
             th (fn [t] [:th s t])
             td (fn [t] [:td s t])]
         [:<>
          [:thead
           [:tr {:style {:font-weight "bolder"
                         :background  bc}}
            [th "User Name"]
            [th "Email Address"]
            [th "User Role"]
            [th "User Status"]
            [th "Organization Name"]]]
          [:tbody
           (for [{:keys [name email user-role organization-name org-membership-status]} @selected-rows]
             ^{:key name}
             [:tr
              [td name]
              [td email]
              [td (roles/type->label user-role)]
              [td org-membership-status]
              [td organization-name]])]])]]
     [:div {:style {:display          "flex"
                    :flex-direction   "row"
                    :justify-content  "end"
                    :gap              "10px"
                    :border-top-color bc
                    :border-top-width "3px"
                    :border-top-style "solid"
                    :padding          "15px"}}
      [buttons/ghost {:text     "Cancel"
                      :on-click on-click-close-dialog}]
      [buttons/primary {:text     "Confirm"
                        :on-click #(on-click @org-name)}]]]))

;; same as confirm-dialog in delete_user.cljs
(defn drop-down
  [{:keys [on-click-update-users get-selected-rows checked] :as m}]
  (r/with-let [dialog-elem (atom nil)]
    ;;TODO find why does this need no wrap when the other buttons don't?
    [:div {:style {:white-space "nowrap"
                   :width       "100%"}}
     ;;TODO consider making the background darker with a pseudo background.
     [:dialog {:ref   #(reset! dialog-elem %)
               :style {:border        "none"
                       :padding       "0px"
                       :border-radius "10px"
                       :overflow      "visible"}}
      [:div {:style {:overflow "hidden"}}
       [confirm-modal
        (merge m
               {:on-click-close-dialog #(.close @dialog-elem)
                :selected-user-info    @selected-rows
                :on-click              (fn [org-name]
                            (on-click-update-users @checked org-name)
                            (.close @dialog-elem))})]]]
     [buttons/drop-down
      (assoc m
             :on-click
             (fn [_]
               (fn []
                 (reset! selected-rows (get-selected-rows))
                 (.showModal @dialog-elem))))]]))
