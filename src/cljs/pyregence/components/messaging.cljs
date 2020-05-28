(ns pyregence.components.messaging
  (:require [herb.core :refer [<class]]
            [reagent.core :as r]
            [clojure.core.async :refer [chan go >! <! timeout]]
            [clojure.string :as str]
            [pyregence.styles :as $]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce toast-message-text (r/atom nil))

(defonce toast-message-chan (chan))

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
