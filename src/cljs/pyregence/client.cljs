(ns ^:figwheel-hooks pyregence.client
  (:require [goog.dom :as dom]
            [reagent.dom :refer [render]]
            [clojure.string :as str]
            [pyregence.pages.tool :as tool]))

(def uri->root-component
  {"/tool" tool/root-component})

(defn render-root [params]
  (let [root-component (-> js/window .-location .-pathname uri->root-component)]
    (render [root-component params] (dom/getElement "app"))))

(defn ^:export init [params]
  (render-root (js->clj params :keywordize-keys true)))

(defn safe-split [str pattern]
  (if (str/blank? str)
    []
    (str/split str pattern)))

(defn ^:after-load mount-root! []
  (.log js/console "Rerunning init function for figwheel.")
  (let [params (reduce (fn [acc cur]
                         (let [[k v] (str/split cur "=")]
                           (assoc acc (keyword k) v)))
                       {}
                       (-> (-> js/window .-location .-search)
                           (str/replace #"^\?" "")
                           (safe-split "&")))]
    (init params)))
