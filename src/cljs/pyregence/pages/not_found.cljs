(ns pyregence.pages.not-found
  (:require-macros [pyregence.herb-patch :refer [style->class]])
  (:require herb.core
            [goog.dom :as dom]
            [reagent.core :as r]
            [pyregence.styles :as $]
            [pyregence.utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component []
  [:div {:style ($/root)}
   [:h1 "Page Not Found"]
   [:input {:class (style->class $/p-button)
            :style ($/combine [$/bg-color :sig-green] [$/align :block :center])
            :type "button"
            :value "Go To Landing Page"
            :on-click #(u/jump-to-url! "/")}]])

(defn ^:export init [_]
  (r/render root-component (dom/getElement "app")))
