(ns ^:figwheel-hooks pyregence.client
  (:require
   [goog.dom                           :as dom]
   [pyregence.components.page-layout   :refer [wrap-page]]
   [pyregence.components.vega          :as vega]
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
   [reagent.dom                        :refer [render]]))

(defonce ^:private original-params  (atom {}))
(defonce ^:private original-session (atom {}))

(def ^:private uri->root-component-h
  "All root-components for URIs that should have just a header."
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
  (render
   [vega/lol]
   (dom/getElement "app")))

(defn- ^:after-load mount-root!
  "A hook for figwheel to call the init function again."
  []
  (println "Rerunning init function for figwheel.")
  (init nil nil))
