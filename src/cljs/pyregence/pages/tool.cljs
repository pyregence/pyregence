(ns pyregence.pages.tool
  (:require [pyregence.styles :as $]))

(defn root-component [_]
  [:div {:style ($/root)}
   [:h1 "This is a blank page. Put something here."]])
