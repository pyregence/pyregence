(ns pyregence.components.messaging
  (:require-macros [pyregence.herb-patch :refer [style->class]])
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [clojure.core.async :refer [chan go >! <! timeout]]
            [clojure.string :as str]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def blank-message-box {:title  ""
                        :body   ""
                        :mode   :none
                        :action nil})

(def message-box-content (r/atom blank-message-box))
(def toast-message-text  (r/atom nil))

(def toast-message-chan (chan))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn toast-message! [message]
  (go (>! toast-message-chan message)))

(defn process-toast-messages! []
  (go (loop [message (<! toast-message-chan)]
        (reset! toast-message-text message)
        (<! (timeout 5000))
        (reset! toast-message-text nil)
        (<! (timeout 500))
        (recur (<! toast-message-chan)))))

(defn set-message-box-content!
  "Sets message content map with merge. Content includes title, body, and mode.
   The message box will show when title is not an empty string.
   Mode can be either :close or nil."
  [content]
  (swap! message-box-content merge content))

(defn close-message-box!
  "Sets message content map to empty values."
  []
  (reset! message-box-content blank-message-box))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn $alert-box []
  {:align-items     "center"
   :background      "white"
   :border          "1.5px solid #009"
   :border-radius   "4px"
   :display         "flex"
   :flex-direction  "row"
   :flex-wrap       "nowrap"
   :font-size       ".9rem"
   :font-style      "italic"
   :justify-content "space-between"
   :left            "1rem"
   :padding         ".5rem"
   :position        "fixed"
   :width           "50%"
   :z-index         "10000"})

(defn $alert-transition [show-full?]
  (if show-full?
    {:transition "opacity 500ms ease-in"
     :opacity    "1"
     :top        "1rem"}
    {:transition "opacity 500ms ease-out"
     :opacity    "0"
     :top        "-100rem"}))

(defn $p-alert-close []
  (with-meta
    {:border-radius "4px"
     :cursor        "pointer"
     :font-weight   "bold"
     :padding       ".5rem .75rem .5rem .5rem"}
    {:pseudo {:hover {:background-color ($/color-picker :black 0.15)}}}))

(defn $message-box []
  {:min-width "35%"
   :max-width "55%"
   :margin    "15% auto"
   :width     "fit-content"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn interpose-react [tag items]
  [:<> (for [i (butlast items)]
         ^{:key i} [:<> i tag])
   (last items)])

(defn show-line-break [text]
  (let [items (if (coll? text)
                (vec text)
                (str/split text #"\n"))]
    (if (<= (count items) 10)
      [:<> (interpose-react [:br] items)]
      (do
        (doseq [i items] (println i))
        [:<> (interpose-react [:br] (conj (subvec items 0 9)
                                          "See console for complete list."))]))))
(defn toast-message []
  (let [message (r/atom "")]
    (fn []
      (let [message? (not (nil? @toast-message-text))]
        (when message? (reset! message @toast-message-text))
        [:div#toast-message {:style ($/combine $alert-box [$alert-transition message?])}
         [:span {:style ($/padding ".5rem")}
          (show-line-break @message)]
         [:span {:class (<class $p-alert-close)
                 :on-click #(reset! toast-message-text nil)}
          "\u274C"]]))))

(defn button [label color & callback]
  [:input {:class (style->class $/p-button)
           :style ($/combine [$/bg-color color] [$/margin "1rem" :h])
           :type "button"
           :value label
           :on-click (when (seq callback) (first callback))}])

(defn message-box-modal []
  (let [{:keys [title body mode action]} @message-box-content]
    (when-not (= "" title)
      [:div {:style ($/modal)}
       [:div {:style ($/combine $message-box [$/align :text :left])}
        [:div {:style ($/action-box)}
         [:div {:style ($/action-header)}
          [:label {:style ($/padding "1px" :l)} title]]
         [:div {:style ($/combine $/flex-col {:padding "1rem"})}
          [:label {:style {:font-size ".95rem"}} (show-line-break body)]
          (condp = mode
            :close [:div {:style ($/combine [$/align :flex :right] [$/margin "1.25rem" :t])}
                    [button "Close" :yellow #(do
                                               (when action (action))
                                               (close-message-box!))]]
            [:<>])]]]])))
