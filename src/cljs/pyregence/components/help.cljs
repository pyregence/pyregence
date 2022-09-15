(ns pyregence.components.help
  (:require [pyregence.components.messaging :refer [set-message-box-content!]]
            [pyregence.state                :as !]
            [pyregence.utils.browser-utils  :as u-browser]))

;;; Help Dialogs

(defn- get-help-dialog [dialog]
  (-> {:terrain {:title "3D Terrain Enabled"
                 :body  (if @!/mobile?
                          "You have enabled 3D Terrain. Use two fingers to tilt or rotate the map."
                          "You have enabled 3D Terrain. Click and drag using your right mouse button to tilt or rotate the map.")}}
      (get dialog)))

;;; Session Helpers

(defn- set-help-seen! [dialog]
  (-> (u-browser/get-local-storage)
      (update-in [:help] merge {dialog true})
      (u-browser/set-local-storage!)))

(defn- seen-help? [dialog]
  (get-in (u-browser/get-local-storage) [:help dialog]))

;;; Public Functions

(defn show-help!
  "Shows the help modal popup for the given dialog and device."
  [dialog & [always-show]]
  {:pre [(get-help-dialog dialog)]}
  (when (or always-show (not (seen-help? dialog)))
    (set-help-seen! dialog)
    (set-message-box-content! (-> (get-help-dialog dialog)
                                  (assoc :mode :close)))))
