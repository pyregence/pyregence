(ns pyregence.components.help
  (:require [pyregence.utils :as u]
            [pyregence.components.messaging :refer [set-message-box-content!]]))

;;; Help Dialogs

(defn get-help-dialog [dialog mobile?]
  (-> {:terrain {:title "3D Terrain Enabled"
                 :body  (if mobile?
                          "You have enabled 3D Terrain. Use two fingers to tilt or rotate the map."
                          "You have enabled 3D Terrain. Click and drag using your right mouse button to tilt or rotate the map.")}}
      (get dialog)))

;;; Session Helpers

(defn- set-help-seen! [dialog]
  (-> (u/get-local-storage)
      (update-in [:help] merge {dialog true})
      (u/set-local-storage!)))

(defn- seen-help? [dialog]
  (get-in (u/get-local-storage) [:help dialog]))

;;; Public Functions

(defn show-help!
  [dialog & [mobile? always-show]]
  {:pre [(get-help-dialog dialog mobile?)]}
  (when (or always-show (not (seen-help? dialog)))
    (set-help-seen! dialog)
    (set-message-box-content! (-> (get-help-dialog dialog mobile?)
                                  (assoc :mode :close)))))
