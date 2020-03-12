(ns firesage.pages.home
  (:require [goog.dom :as dom]
            [reagent.core :as r]
            [firesage.styles :as $]))

(defn root-component []
  [:div {:style ($/root)}
   [:h1 "This is the default home page. Put something here."]])

(defn ^:export init [_]
  (r/render root-component (dom/getElement "app")))
