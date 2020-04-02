(ns ^:figwheel-hooks pyregence.client
  (:require [goog.dom :as dom]
            [reagent.core :as r]
            [clojure.string :as str]
            [pyregence.pages.tool :as tool]
            [pyregence.pages.not-found :as not-found]))

(defn render-root [_]
  (let [uri (-> js/window .-location .-pathname)]
    (r/render (if (= "/tool" uri)
                [tool/root-component]
                [not-found/root-component])
              (dom/getElement "app"))))

(defn ^:export init [params]
  (render-root (js->clj params :keywordize-keys true))) ; TODO this can probably be done the same as figwheel.

(defn safe-split [str pattern]
  (if (str/blank? str)
    []
    (str/split str pattern)))

(defn ^:after-load mount-root! []
  (.log js/console (str "Rerunning init function for figwheel."))
  (let [params (reduce (fn [acc cur]
                         (let [[k v] (str/split cur "=")]
                           (assoc acc (keyword k) v)))
                       {}
                       (-> (-> js/window .-location .-search)
                           (str/replace #"^\?" "")
                           (safe-split "&")))]
    (init params)))
