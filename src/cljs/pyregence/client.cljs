(ns ^:figwheel-hooks pyregence.client
  (:require [goog.dom                           :as dom]
            [reagent.dom                        :refer [render]]
            [pyregence.state                    :as !]
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
            [pyregence.pages.verify-email       :as verify-email]
            [pyregence.components.page-layout   :refer [wrap-page]]))

(defonce ^:private original-params (atom {}))

(def ^:private uri->root-component-ha
  "All root-components for URIs that should have a header and announcement-banner."
  {"/"                   #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/admin"              admin/root-component
   "/dashboard"          dashboard/root-component
   "/forecast"           #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/login"              login/root-component
   "/long-term-forecast" #(ntf/root-component (merge % {:forecast-type :long-term}))
   "/near-term-forecast" #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/register"           register/root-component
   "/reset-password"     reset-password/root-component
   "/verify-email"       verify-email/root-component})

(def ^:private uri->root-component-hf
  "All root-components for URIs that should have a header and a footer."
  {"/help"           help/root-component
   "/privacy-policy" privacy/root-component
   "/terms-of-use"   terms/root-component})

(defn- render-root
  "Renders the root component for the current URI."
  [params]
  (let [uri (-> js/window .-location .-pathname)]
    (render (cond
              (uri->root-component-ha uri)
              (wrap-page #((uri->root-component-ha uri) params))

              (uri->root-component-hf uri)
              (wrap-page #((uri->root-component-hf uri) params)
                         :footer? true)

              :else
              (wrap-page not-found/root-component
                         :footer? true))
            (dom/getElement "app"))))

(defn- ^:export init
  "Defines the init function to be called from window.onload()."
  [params]
  (let [clj-params (js->clj params :keywordize-keys true)
        cur-params (if (seq clj-params)
                     (reset! original-params
                             (js->clj params :keywordize-keys true))
                     @original-params)]
    (reset! !/dev-mode? (:dev-mode cur-params))
    (reset! !/feature-flags (:features cur-params))
    (reset! !/mapbox-access-token (get-in cur-params [:mapbox :access-token]))
    (reset! !/geoserver-urls (:geoserver cur-params))
    (reset! !/default-forecasts (get cur-params :default-forecasts))
    (reset! !/pyr-auth-token (get cur-params :pyr-auth-token))
    (render-root cur-params)))

(defn- ^:after-load mount-root!
  "A hook for figwheel to call the init function again."
  []
  (println "Rerunning init function for figwheel.")
  (init {}))
