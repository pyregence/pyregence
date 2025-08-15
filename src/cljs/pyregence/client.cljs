(ns ^:figwheel-hooks pyregence.client
  (:require [clojure.core.async                 :refer [go <!]]
            [clojure.edn                        :as edn]
            [goog.dom                           :as dom]
            [reagent.dom                        :refer [render]]
            [pyregence.components.page-layout   :refer [wrap-page]]
            [pyregence.pages.admin              :as admin]
            [pyregence.pages.backup-codes       :as backup-codes]
            [pyregence.pages.dashboard          :as dashboard]
            [pyregence.pages.help               :as help]
            [pyregence.pages.login              :as login]
            [pyregence.pages.near-term-forecast :as ntf]
            [pyregence.pages.not-found          :as not-found]
            [pyregence.pages.privacy-policy     :as privacy]
            [pyregence.pages.register           :as register]
            [pyregence.pages.reset-password     :as reset-password]
            [pyregence.pages.settings           :as settings]
            [pyregence.pages.terms-of-use       :as terms]
            [pyregence.pages.totp-setup         :as totp-setup]
            [pyregence.pages.verify-2fa         :as verify-2fa]
            [pyregence.pages.verify-email       :as verify-email]
            [pyregence.state                    :as !]
            [pyregence.utils.async-utils        :as u-async]))

(defonce ^:private original-params  (atom {}))
(defonce ^:private original-session (atom {}))

(def ^:private uri->root-component-h
  "All root-components for URIs that should have just a header."
  {"/"                   #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/admin"              admin/root-component
   "/backup-codes"       backup-codes/root-component
   "/dashboard"          dashboard/root-component
   "/forecast"           #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/login"              login/root-component
   "/long-term-forecast" #(ntf/root-component (merge % {:forecast-type :long-term}))
   "/near-term-forecast" #(ntf/root-component (merge % {:forecast-type :near-term}))
   "/register"           register/root-component
   "/reset-password"     reset-password/root-component
   "/settings"           settings/root-component
   "/totp-setup"         totp-setup/root-component
   "/verify-2fa"         verify-2fa/root-component
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
              (uri->root-component-h uri)
              (wrap-page #((uri->root-component-h uri) params))

              (uri->root-component-hf uri)
              (wrap-page #((uri->root-component-hf uri) params)
                         :footer? true)

              :else
              (wrap-page not-found/root-component
                         :footer? true))
            (dom/getElement "app"))))

(defn- ^:export init
  "Defines the init function to be called from window.onload()."
  [params session]
  (go
    (let [clj-params    (if params
                          (reset! original-params (js->clj params :keywordize-keys true))
                          @original-params)
          clj-session   (if session
                          (reset! original-session (js->clj session :keywordize-keys true))
                          @original-session)
          merged-params (merge clj-params clj-session)]
      (reset! !/show-disclaimer?    (get clj-session :show-disclaimer))
      (reset! !/feature-flags       (get clj-session :features))
      (reset! !/geoserver-urls      (get clj-session :geoserver))
      (reset! !/default-forecasts   (get clj-session :default-forecasts))
      (reset! !/pyr-auth-token      (get clj-session :auth-token))
      (reset! !/mapbox-access-token (get clj-session :mapbox-access-token))
      (render-root merged-params))))

(defn- ^:after-load mount-root!
  "A hook for figwheel to call the init function again."
  []
  (println "Rerunning init function for figwheel.")
  (init nil nil))
