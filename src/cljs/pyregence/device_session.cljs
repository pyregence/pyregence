(ns pyregence.device-session
  "The browser-profile identity and the short critical section around operations
   that may replace its signed session cookie. Tabs share localStorage, while a
   different profile receives a different identifier."
  (:require [clojure.core.async :refer [<! chan close! go put!]]))

(def ^:private device-key "pyrecast-device-id")
(def ^:private transition-lock "pyrecast-session-transition")
(def ^:private activity-key-prefix "pyrecast-session-server-last-active:")

(defonce ^:private !fallback-device (atom nil))
(defonce ^:private !generation (atom nil))
(defonce ^:private !last-active (atom nil))

(defn- stored-device []
  (try (.getItem (.-localStorage js/window) device-key)
       (catch :default _ @!fallback-device)))

(defn- store-device! [device]
  (reset! !fallback-device device)
  (try (.setItem (.-localStorage js/window) device-key device)
       (catch :default _ nil))
  device)

(defn device-id
  "The identifier shared by tabs in this origin-storage profile."
  []
  (or (stored-device)
      (store-device! (str (random-uuid)))))

(defn- activity-key [generation]
  (str activity-key-prefix generation))

(defn- stored-last-active []
  (or (when-let [generation @!generation]
        (try
          (let [value (js/Number (.getItem (.-localStorage js/window)
                                           (activity-key generation)))]
            (when (js/Number.isFinite value) value))
          (catch :default _ nil)))
      @!last-active))

(defn note-server-activity!
  "Share the newest server-acknowledged activity version across every tab in
   this browser profile. A tab that outlives the reporter can then qualify its
   later idle logout with the same authoritative version."
  [last-active]
  (when last-active
    (let [newest (max (or (stored-last-active) last-active) last-active)]
      (reset! !last-active newest)
      (when-let [generation @!generation]
        (try (.setItem (.-localStorage js/window) (activity-key generation) (str newest))
             (catch :default _ nil))))))

(defn install!
  "Adopt the server's current device identity after SSO/legacy login and remember
   the page's login generation and acknowledged activity for qualified commands."
  [server-device generation last-active]
  (when server-device (store-device! server-device))
  (reset! !generation generation)
  (reset! !last-active nil)
  (note-server-activity! last-active))

(defn generation
  "The login generation rendered into this page."
  []
  @!generation)

(defn request-headers
  "Headers identifying this browser profile to a session-aware request."
  []
  {"X-PyreCast-Device-Id" (device-id)})

(defn logout-command
  "A command qualified by the generation and activity version this page saw."
  [reason]
  {:expected-generation @!generation
   :expected-last-active (stored-last-active)
   :scope :current
   :reason reason})

(defn cleanup-command
  "A cleanup request qualified by the generation this page rendered."
  [scope]
  {:expected-generation @!generation
   :scope scope})

(defn serialized!
  "Run OPERATION while this browser profile owns the cookie-transition lock.
   The controlled local acceptance origin is the only lock-free exception.
   Elsewhere, an unavailable Web Locks API fails closed rather than pretending
   a document-local critical section protects a browser profile."
  [operation]
  (let [result (chan)
        run    (fn []
                 (js/Promise.
                  (fn [resolve-promise reject-promise]
                    (go
                      (try
                        (put! result (<! (operation)))
                        (close! result)
                        (resolve-promise true)
                        (catch :default error
                          (close! result)
                          (reject-promise error)))))))]
    (cond
      (.-locks js/navigator) (.request (.-locks js/navigator) transition-lock run)
      (= "local.pyrecast.org" (.-hostname (.-location js/window))) (run)
      :else (close! result))
    result))
