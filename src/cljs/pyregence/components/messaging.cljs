(ns pyregence.components.messaging
  (:require [herb.core          :refer [<class]]
            [reagent.core       :as r]
            [clojure.core.async :refer [chan go >! <! timeout]]
            [clojure.string     :as str]
            [pyregence.components.svg-icons :as svg]
            [pyregence.state    :as !]
            [pyregence.styles   :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private blank-message-box {:title         ""
                                  :body          ""
                                  :mode          :none ; :close, :confirm, or :custom
                                  :action        nil
                                  :custom-button nil
                                  :cancel-fn     nil})
(def ^:private message-box-content (r/atom blank-message-box))
(def ^:private toast-message-text  (r/atom nil))
(def ^:private toast-message-chan  (chan))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toast-message!
  "Puts a message onto the toast message channel."
  [message]
  (go (>! toast-message-chan message)))

(defn process-toast-messages!
  "Perpetually takes a message off of the toast message channel and updates the appropriate atom.
   Waits 5.5 seconds before looking for the next message on the channel."
  []
  (go (loop [message (<! toast-message-chan)]
        (reset! toast-message-text message)
        (<! (timeout 5000))
        (reset! toast-message-text nil)
        (<! (timeout 500))
        (recur (<! toast-message-chan)))))

(defn set-message-box-content!
  "Sets message content map with merge. Content includes title, body, mode, action,
   and custom-button If mode is :custom, then the custom-button key must have
   the desired Reagent button that you want to render.
   The message box will show when title is not an empty string.
   Mode can be either :close, :confirm, :custom, or nil.
   Action is optional and will be executed when the mode button is clicked."
  [content]
  (swap! message-box-content merge content))

(defn- close-message-box!
  "Sets message content map to empty values."
  []
  (reset! message-box-content blank-message-box))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- $alert-box []
  (with-meta
    {:align-items     "center"
     :background      "white"
     :border          (str "1.5px solid " ($/color-picker :black))
     :border-radius   "4px"
     :box-shadow      (str "1px 0 5px " ($/color-picker :dark-gray 0.3))
     :display         "flex"
     :flex-direction  "row"
     :flex-wrap       "nowrap"
     :font-size       "1rem"
     :font-style      "italic"
     :font-weight     "bold"
     :justify-content "space-between"
     :left            "1rem"
     :max-width       (if @!/mobile? "90%" "50%")
     :padding         ".5rem"
     :position        "fixed"
     :z-index         "10000"}
    {:media {{:max-width "800px"}
             {:width "90%"}}}))

(defn- $alert-transition [show-full?]
  (if show-full?
    {:opacity    "1"
     :top        "1rem"
     :transition "opacity 500ms ease-in"}
    {:opacity    "0"
     :top        "-100rem"
     :transition "opacity 500ms ease-out"}))

(defn- $p-alert-close []
  (with-meta
    {:border-radius "4px"
     :cursor        "pointer"
     :fill          ($/color-picker :brown)
     :font-weight   "bold"
     :padding       ".25rem"}
    {:pseudo {:hover {:background-color ($/color-picker :brown .5)
                      :fill             ($/color-picker :white)}}}))

(defn- $message-box []
  {:margin    "10% auto"
   :min-width "35%"
   :width     "fit-content"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- interpose-react [tag items]
  [:<> (for [i (butlast items)]
         ^{:key i} [:<> i tag])
   (last items)])

(defn- show-line-break [text]
  (let [items (if (coll? text)
                (vec text)
                (str/split text #"\n"))]
    (if (<= (count items) 10)
      [:<> (interpose-react [:br] items)]
      (do
        (doseq [i items] (println i))
        [:<> (interpose-react [:br] (conj (subvec items 0 9)
                                          "See console for complete list."))]))))
(defn toast-message
  "Creates a toast message component."
  []
  (let [message (r/atom "")]
    (fn []
      (let [message? (not (nil? @toast-message-text))]
        (when message? (reset! message @toast-message-text))
        [:div#toast-message {:class (<class $alert-box)
                             :style ($alert-transition message?)}
         [:span {:style {:padding ".5rem .75rem .5rem .5rem"}}
          (show-line-break @message)]
         [:span {:class    (<class $p-alert-close)
                 :on-click #(reset! toast-message-text nil)}
          [svg/close :height "26px" :width "26px"]]]))))

(defn- button [label & callback]
  [:input {:class    (<class $/p-form-button)
           :style    ($/margin "1rem" :h)
           :type     "button"
           :value    label
           :on-click (when (seq callback) (first callback))}])

(defn message-box-modal
  "Creates a message box modal component."
  []
  (let [{:keys [title body mode action custom-button cancel-fn]} @message-box-content]
    (when-not (= "" title)
      [:div {:style ($/combine $/modal {:position "fixed"})}
       [:div {:style ($/combine $message-box [$/align :text :left])}
        [:div {:style ($/action-box)}
         [:div {:style ($/action-header)}
          [:label {:style ($/padding "1px" :l)} title]]
         [:div {:style ($/combine $/flex-col {:padding "1rem"})}
          (if (vector? body)
            body
            [:label {:style {:font-size ".95rem"}}
             (show-line-break body)])
          (condp = mode
            :close   [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                      [button "Close" #(do (when action (action))
                                           (close-message-box!))]]
            :confirm [:div {:style {:align-content "space-between"
                                    :display       "flex"
                                    :margin-top    "4px"}}
                      [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                       [button "No, Cancel" #(do (when cancel-fn (cancel-fn))
                                                 (close-message-box!))]]
                      [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                       [button "Yes, Continue" #(do (action)
                                                    (close-message-box!))]]]
            :custom  [:div {:style {:align-content "space-between"
                                    :display       "flex"
                                    :margin-top    "4px"}}
                      [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                       custom-button]
                      [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                       [button "Close" #(do (when action (action))
                                            (close-message-box!))]]]
            [:<>])]]]])))
