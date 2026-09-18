(ns pyregence.archetypes.session-activity.local-storage
  "Browser-local session activity shared across tabs through localStorage."
  (:require [cljs.reader                     :as edn]
            [pyregence.api.session-activity :as session-activity]))

(def ^:private last-input-key
  "pyregence-session-last-input-at")

(def ^:private last-reported-input-key
  "pyregence-session-last-reported-input-at")

(def ^:private input-write-interval-ms
  "How often continuous input is worth copying into synchronous browser
   storage. One second keeps another tab's view comfortably inside the idle
   watch's fifteen-second safety margin without writing on every mousemove."
  1000)

(defn- read-at
  "The timestamp held under KEY, or nil before any tab has written one."
  [storage-key]
  (when-let [stored (.getItem (.-localStorage js/window) storage-key)]
    (let [at (edn/read-string stored)]
      (when (number? at) at))))

(defn- write-at!
  "Make AT visible under KEY to every tab on this origin."
  [storage-key at]
  (.setItem (.-localStorage js/window) storage-key (pr-str at)))

(defn- ready-to-share?
  "Whether AT is far enough past the last input this tab shared to write again."
  [last-shared-at at]
  (or (nil? last-shared-at)
      (>= (- at last-shared-at) input-write-interval-ms)))

(defrecord LocalStorageSessionActivity [!last-shared-at]
  session-activity/ISessionActivity
  (last-input-at [_]
    (read-at last-input-key))

  (last-reported-input-at [_]
    (read-at last-reported-input-key))

  (note-input-at! [_ at]
    (when (ready-to-share? @!last-shared-at at)
      (reset! !last-shared-at at)
      (write-at! last-input-key at)))

  (note-reported-input-at! [_ at]
    (write-at! last-reported-input-key at)))

(defn SessionActivity
  "A session activity carried by storage shared across browser tabs."
  []
  (->LocalStorageSessionActivity (atom nil)))
