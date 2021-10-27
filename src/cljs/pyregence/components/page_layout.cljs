(ns pyregence.components.page-layout
  (:require [reagent.core       :as r]
            [herb.core          :refer [<class]]
            [clojure.string     :as str]
            [clojure.core.async :refer [<! go timeout]]
            [pyregence.styles   :as $]
            [pyregence.components.svg-icons :refer [close]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; State
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce announcement (r/atom ""))

(defn set-announcement-text!
  "Sets the text for the announcement banner given the value from `config.edn`."
  [text]
  (reset! announcement text))

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

(defn- announcement-banner []
  (let [show? (r/atom (pos? (count @announcement)))]
    (go
      (<! (timeout 7500))
      (reset! show? false))
    (fn []
      [:div#banner {:style {:background-color "#f96841"
                            :box-shadow       "3px 1px 4px 0 rgb(0, 0, 0, 0.25)"
                            :color            "#ffffff"
                            :display          (if @show? "block" "none")
                            :margin           "0px"
                            :padding          "5px"
                            :position         "fixed"
                            :text-align       "center"
                            :top              "0"
                            :width            "100vw"
                            :z-index          100}}
       [:p {:style {:font-size   "18px"
                    :font-weight "bold"
                    :margin      "0 30px 0 0"}}
        @announcement]
       [:button {:class   (<class $/p-button :transparent :white :white :white :black)
                 :style   {:border-radius "50%"
                           :padding       "0"
                           :position      "fixed"
                           :right         "10px"
                           :top           "5px"}
                 :on-click #(reset! show? false)}
        [:div {:style {:height "23px"
                       :width  "23px"}}
         [close]]]])))

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
  [:<>
   [header]
   (when-not (empty? @announcement)
     [announcement-banner])
   [root-component]
   (when footer?
     [footer])])
