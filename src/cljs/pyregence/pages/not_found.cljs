(ns pyregence.pages.not-found
  (:require-macros [pyregence.herb-patch :refer [style->class]])
  (:require herb.core
            [pyregence.styles :as $]
            [pyregence.utils  :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component []
  [:div {:style ($/root)}
   [:h1 "Page Not Found"]
   [:input {:class (style->class $/p-button)
            :style ($/combine [$/bg-color :sig-green] [$/align :block :center] {:margin-top "2rem"})
            :type "button"
            :value "Go To Landing Page"
            :on-click #(u/jump-to-url! "/")}]])
