(ns ^:figwheel-hooks pyregence.client
  (:require [clojure.core.async                 :refer [go <!]]
            [clojure.edn                        :as edn]
            [goog.dom                           :as dom]
            [reagent.dom                        :refer [render]]
            [pyregence.components.page-layout   :refer [wrap-page]]
            [pyregence.pages.account-settings   :as account-settings]
            [pyregence.pages.backup-codes       :as backup-codes]
            [pyregence.pages.dashboard          :as dashboard]
            [pyregence.pages.disable-2fa        :as disable-2fa]
            [pyregence.pages.help               :as help]
            [pyregence.pages.login              :as login]
            [pyregence.pages.near-term-forecast :as ntf]
            [pyregence.pages.not-found          :as not-found]
            [pyregence.pages.privacy-policy     :as privacy]
            [pyregence.pages.register           :as register]
            [pyregence.pages.reset-password     :as reset-password]
            [pyregence.pages.terms-of-use       :as terms]
            [pyregence.pages.setup-2fa          :as setup-2fa]
            [pyregence.pages.switch-2fa         :as switch-2fa]
            [pyregence.pages.verify-2fa         :as verify-2fa]
            [pyregence.pages.verify-email       :as verify-email]
            [pyregence.archetypes.directory     :refer [=>Directory]]
            [pyregence.clock                    :as clock]
            [pyregence.datatypes.idle-window    :as idle-window]
            [pyregence.datatypes.session        :as session]
            [pyregence.session-watch            :as session-watch]
            [pyregence.wiring                   :as wiring]
            [pyregence.state                    :as !]
            [pyregence.utils.async-utils        :as u-async]))

(defonce ^:private original-params  (atom {}))
(defonce ^:private original-session (atom {}))

(def ^:private uri->root-component-h
  "All root-components for URIs that should have just a header.

   Builders rather than components. Init is the only thing that builds a
   collaborator, so a page that needs one is handed it here; `constantly` is a
   page that needs nothing. The alternative -- a page reaching into a holder for
   what init built -- is dependency injection with the injection left out, and
   makes every page a possible caller of every concept."
  {"/"                   (fn [d] #(ntf/root-component d (merge % {:forecast-type :near-term})))
   "/account-settings"   (fn [d] #(account-settings/root-component d %))
   "/backup-codes"       (constantly backup-codes/root-component)
   "/dashboard"          (constantly dashboard/root-component)
   "/disable-2fa"        (constantly disable-2fa/root-component)
   "/forecast"           (fn [d] #(ntf/root-component d (merge % {:forecast-type :near-term})))
   "/login"              (constantly login/root-component)
   "/long-term-forecast" (fn [d] #(ntf/root-component d (merge % {:forecast-type :long-term})))
   "/near-term-forecast" (fn [d] #(ntf/root-component d (merge % {:forecast-type :near-term})))
   "/register"           (constantly register/root-component)
   "/reset-password"     (constantly reset-password/root-component)
   "/setup-2fa"          (constantly setup-2fa/root-component)
   "/switch-2fa"         (constantly switch-2fa/root-component)
   "/verify-2fa"         (constantly verify-2fa/root-component)
   "/verify-email"       (constantly verify-email/root-component)})

(def ^:private uri->root-component-hf
  "All root-components for URIs that should have a header and a footer. Builders,
   as above."
  {"/help"           (constantly help/root-component)
   "/privacy-policy" (constantly privacy/root-component)
   "/terms-of-use"   (constantly terms/root-component)})
(defn- render-root
  "Renders the root component for the current URI, built with what init built."
  [params a-directory]
  (let [uri           (.. js/window -location -pathname)
        build-h       (get uri->root-component-h uri)
        build-hf      (get uri->root-component-hf uri)
        build         (or build-h build-hf (constantly not-found/root-component))
        footer?       (some? build-hf)]
    (render
     [wrap-page {:root-component (build a-directory)
                 :params         params
                 :footer?        footer?}]
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
      (reset! !/usage-terms-and-conditions-date (get clj-session :usage-terms-and-conditions-date))
      (reset! !/feature-flags                   (get clj-session :features))
      (reset! !/geoserver-urls                  (get clj-session :geoserver))
      (reset! !/default-forecasts               (get clj-session :default-forecasts))
      (reset! !/pyr-auth-token                  (get clj-session :auth-token))
      (reset! !/mapbox-access-token             (get clj-session :mapbox-access-token))
      ;; This is the root, so this is where the page's sense of the present comes
      ;; from. Installed before anything is rendered, because a component that
      ;; reads the clock during its first mount would otherwise find none.
      (clock/install! (clock/->SystemClock))
      ;; PYR1-1623: watch the session for the moment it goes quiet too long. The
      ;; window is PyreCast's own, reported with the page and only to somebody
      ;; who has a session to lose -- so an anonymous visitor arrives here with
      ;; nothing, `->idle-window` answers nothing, and `watch!` declines to
      ;; watch. No test of who this is belongs at this end.
      (session-watch/watch! (idle-window/->idle-window (:idle-timeout-min clj-session)))
      ;; The composition root, and the only one. Nothing below builds a
      ;; collaborator for itself or reaches for one: a page is handed what it
      ;; needs and speaks to it through its protocol. The wiring is in force for
      ;; the construction and for nothing else.
      (render-root merged-params
                   (wiring/with-wiring (wiring/->BrowserWiring)
                     (=>Directory (session/->session (:user-role merged-params))))))))

(defn- ^:after-load mount-root!
  "A hook for figwheel to call the init function again."
  []
  (println "Rerunning init function for figwheel.")
  (init nil nil))
