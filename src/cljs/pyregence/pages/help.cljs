(ns pyregence.pages.help)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn root-component [_]
  [:div {:id "help-page"}
   [:h1 {:style {:margin     "5rem 2.5rem"
                 :text-align "center"}}
    "This page is under construction. Please check back later!"]
   [:footer {:style {:background    "#60411f"
                     :margin-bottom "0"
                     :padding       "1rem"}}
    [:p {:style {:color          "white"
                 :font-size      "0.9rem"
                 :margin-bottom  "0"
                 :text-align     "center"
                 :text-transform "uppercase"}}
     (str "\u00A9 "
          (.getFullYear (js/Date.))
          " Pyregence - All Rights Reserved | ")
     [:a {:href  "/terms-of-use"
          :style {:border-bottom "none"
                  :color         "#ffffff"
                  :font-weight   "400"}}
      "Terms"]]]])
