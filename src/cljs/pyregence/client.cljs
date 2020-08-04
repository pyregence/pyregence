(ns ^:figwheel-hooks pyregence.client
  (:require [goog.dom :as dom]
            [reagent.dom :refer [render]]
            [clojure.string :as str]
            [pyregence.pages.admin              :as admin]
            [pyregence.pages.login              :as login]
            [pyregence.pages.near-term-forecast :as ntf]
            [pyregence.pages.register           :as register]
            [pyregence.pages.reset-password     :as reset-password]
            [pyregence.pages.verify-email       :as verify-email]))

(def uri->root-component
  {"/admin"              admin/root-component
   "/forecast"           ntf/root-component
   "/login"              login/root-component
   "/near-term-forecast" ntf/root-component
   "/register"           register/root-component
   "/reset-password"     reset-password/root-component
   "/verify-email"       verify-email/root-component})

(defn render-root [params]
  (let [root-component (-> js/window .-location .-pathname uri->root-component)]
    (render [root-component params] (dom/getElement "app"))))

(defn ^:export init [params]
  (render-root (js->clj params :keywordize-keys true)))

(defn safe-split [str pattern]
  (if (str/blank? str)
    []
    (str/split str pattern)))

;; TODO This needs to be updated for passing server side params through the init function.
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
