(ns pyregence.components.page-layout
  (:require [clojure.string     :as str]
            [pyregence.components.messaging :refer [toast-message
                                                    process-toast-messages!]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI Components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- header []
  (let [pyrecast? (str/ends-with? (-> js/window .-location .-hostname) "pyrecast.org")]
    [:div {:id    "header"
           :style {:align-items     "center"
                   :display         "flex"
                   :justify-content "space-between"}}
     [:a {:rel   "home"
          :href  (if pyrecast? "/" "https://pyregence.org")
          :title "Pyregence"
          :style {:margin-bottom "0.3125rem"
                  :margin-left   "10%"
                  :margin-top    "0.3125rem"}}
      [:img {:src   (str "/images/" (if pyrecast? "pyrecast" "pyregence") "-logo.svg")
             :alt   "Pyregence Logo"
             :style {:height "40px"
                     :width  "auto"}}]]
     (when pyrecast?
       [:a {:href   "https://pyregence.org"
            :target "pyregence"
            :style  {:margin-right "5%"}}
        [:img {:src   "/images/powered-by-pyregence.svg"
               :alt   "Powered by Pyregence Logo"
               :style {:height "1.25rem"
                       :width  "auto"}}]])]))

(defn- footer []
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
     "Terms"]]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Page Layouts
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-page
  "Specifies the content to go inside of the [:body [:div#app]] for a page.
   By default, a page does not include a footer unless specified."
  [root-component & {:keys [footer?]}]
  (process-toast-messages!)
  (fn [_]
    [:<>
     [header]
     [toast-message]
     [root-component]
     (when footer?
       [footer])]))
