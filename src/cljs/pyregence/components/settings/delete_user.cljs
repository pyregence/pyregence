;; TODO this shares logic with the add_user.cljs consider combining them.
(ns pyregence.components.settings.delete-user
  (:require
   [pyregence.components.buttons        :as buttons]
   [pyregence.components.settings.roles :as roles]
   [pyregence.styles                    :as $]
   [reagent.core                        :as r]))

(def selected-rows (r/atom nil))

(defn confirm-modal
  [{:keys [on-click-delete-users! on-click-close-dialog]}]
  (let [bc ($/color-picker :neutral-light-gray)]
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
      "Confirm User Deletion"]
     [:div {:style {:display        "flex"
                    :flex-direction "column"
                    :gap            "10px"}}
      [:p "You are about to permanently delete these users. Are you sure you want to proceed?"]
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
            [th "Organization Name"]]]
          [:tbody
           (for [{:keys [name email user-role organization-name]} @selected-rows]
             ^{:key name}
             [:tr
              [td name]
              [td email]
              [td (roles/type->label user-role)]
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
                        :on-click on-click-delete-users!}]]]))

(defn confirm-dialog
  [{:keys [on-click-delete-users! get-selected-rows]}]
  (r/with-let [dialog-elem (atom nil)]
    ;;TODO find why does this need no wrap when the other buttons don't?
    [:div {:style {:white-space "nowrap"}}
     ;;TODO consider making the background darker with a pseudo background.
     [:dialog {:ref   #(reset! dialog-elem %)
               :style {:border        "none"
                       :padding       "0px"
                       :border-radius "10px"
                       :overflow      "visible"}}
      [:div {:style {:overflow "hidden"}}
       [confirm-modal {:on-click-close-dialog  #(.close @dialog-elem)
                       :selected-user-info     @selected-rows
                       :on-click-delete-users! (fn []
                                                 (on-click-delete-users!)
                                                 (.close @dialog-elem))}]]]
     [buttons/remove-cmpt
      {:text "Delete Users"
       :on-click
       (fn []
         (reset! selected-rows (get-selected-rows))
         (.showModal @dialog-elem))}]]))
