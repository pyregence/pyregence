(ns pyregence.pages.help)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component
  "The root component of the /help page."
  [_]
  (fn [_]
    [:div {:id "help-page"}
     [:h1 {:style {:margin     "5rem 2.5rem"
                   :text-align "center"}}
      "This page is under construction. Please check back later!"]]))
