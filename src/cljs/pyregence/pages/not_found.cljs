(ns pyregence.pages.not-found)

(defn root-component
  "The root component for the 404 page."
  [_]
  (fn [_]
    [:div {:style {:margin-top "100px"}}
     [:div {:style {:align-content   "center"
                    :display         "flex"
                    :justify-content "center"
                    :margin-bottom   "6rem"}}
      [:h1 {:style {:text-align "center"}}
       "404 - Page Not Found"]]]))
