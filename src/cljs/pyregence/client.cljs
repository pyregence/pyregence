(ns ^:figwheel-hooks pyregence.client
  (:require [goog.dom                           :as dom]
            [reagent.dom                        :refer [render]]
            [pyregence.config                   :as c]
            [pyregence.pages.admin              :as admin]
            [pyregence.pages.dashboard          :as dashboard]
            [pyregence.pages.help               :as help]
            [pyregence.pages.login              :as login]
            [pyregence.pages.near-term-forecast :as ntf]
            [pyregence.pages.not-found          :as not-found]
            [pyregence.pages.privacy-policy     :as privacy]
            [pyregence.pages.register           :as register]
            [pyregence.pages.reset-password     :as reset-password]
            [pyregence.pages.terms-of-use       :as terms]
            [pyregence.pages.verify-email       :as verify-email]))

(defonce ^:private original-params (atom {}))

(def ^:private uri->root-component
  {"/"                   #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/admin"              admin/root-component
   "/dashboard"          dashboard/root-component
   "/forecast"           #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/help"               help/root-component
   "/login"              login/root-component
   "/long-term-forecast" #(ntf/root-component (merge % {:forecast-type :long-term}))
   "/near-term-forecast" #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/privacy-policy"     privacy/root-component
   "/register"           register/root-component
   "/reset-password"     reset-password/root-component
   "/terms-of-use"       terms/root-component
   "/verify-email"       verify-email/root-component})

(defn- render-root
  "Renders the root component for the current URI."
  [params]
  (let [root-component (if (:valid? params)
                         (-> js/window .-location .-pathname uri->root-component)
                         not-found/root-component)]
    (render [root-component params] (dom/getElement "app"))))

(defn- ^:export init
  "Defines the init function to be called from window.onload()."
  [params]
  (let [clj-params (js->clj params :keywordize-keys true)
        cur-params (if (seq clj-params)
                     (reset! original-params
                             (js->clj params :keywordize-keys true))
                     @original-params)]
    (c/set-dev-mode! (get-in cur-params [:dev-mode]))
    (c/set-feature-flags! cur-params)
    (c/set-geoserver-base-url! (get-in cur-params [:geoserver :base-url]))
    (c/set-mapbox-access-token! (get-in cur-params [:mapbox :access-token]))
    (render-root cur-params)))

(defn- ^:after-load mount-root!
  "A hook for figwheel to call the init function again."
  []
  (println "Rerunning init function for figwheel.")
  (init {}))
