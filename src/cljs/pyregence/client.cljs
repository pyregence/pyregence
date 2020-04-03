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
                [not-found/root-component]) ; TODO a static html not found is probably more appropriate.
              (dom/getElement "app"))))

(defn ^:export init [params]
  ; TODO params can probably be done the same as figwheel instead of embedding in the JS call.
  (render-root (js->clj params :keywordize-keys true)))

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
