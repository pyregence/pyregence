(ns pyregence.components.map-controls.panel-dropdown
  (:require [pyregence.components.common    :refer [tool-tip-wrapper]]
            [pyregence.components.svg-icons :as svg]
            [pyregence.styles               :as $]
            [pyregence.utils.dom-utils      :as u-dom]))

(defn- options->selected-key
  "given a map of selected-keys to hiccup option tags , return the selected-key
  associated with the option tag that has the selected? key. This solves the
  problem of selecting an option through the option (as opposed to the parent),
  but then passing that information to the parent :select so it can use it in
  the :select/value which react uses instead of an html option selected
  attribute"
  [options]
  (reduce-kv
   (fn [_ potential-key {:keys [selected?]}]
     (when selected? (reduced potential-key)))
   nil
   options))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Root component
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn panel-dropdown [title tool-tip-text val options disabled? call-back & [selected-param-set]]
  [:div {:style {:display "flex" :flex-direction "column" :margin-top ".25rem"}}
   [:div {:style {:display "flex" :justify-content "space-between"}}
    [:label title]
    [tool-tip-wrapper
     tool-tip-text
     :left
     [:div {:style ($/combine ($/fixed-size "1rem")
                              {:margin "0 .25rem 4px 0"
                               :fill   ($/color-picker :font-color)})}
      [svg/help]]]]
   [:select {:style     ($/dropdown)
             :value     (or (options->selected-key options) val :none)
             :disabled  disabled?
             :on-change #(call-back (u-dom/input-keyword %))}
    (map (fn [[key {:keys [opt-label enabled? disabled-for]}]]
           [:option {:key      key
                     :value    key
                     :disabled (or (and (set? disabled-for) (some selected-param-set disabled-for))
                                   (and (fn? enabled?) (not (enabled?))))}
            opt-label])
         options)]])
