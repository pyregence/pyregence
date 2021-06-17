(ns pyregence.components.help
  (:require [pyregence.utils :as u]
            [pyregence.components.messaging :refer [set-message-box-content!]]))

;;; Help Dialogs

(def help-dialogs {:terrain {:title "3D Terrain Enabled"
                             :body  "You have enabled 3D Terrain. Click and drag using your right mouse button to tilt or rotate the map."
                             :mobile "You have enabled 3D Terrain. Use two fingers to tilt or rotate the map."}})

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
  {:pre [((-> help-dialogs keys set) dialog)]}
  (when-not (or always-show (seen-help? dialog))
    (set-help-seen! dialog)
    (set-message-box-content! (merge (dialog help-dialogs)
                                     (when mobile?
                                       {:body (get-in help-dialogs [dialog :mobile])})
                                     {:mode :close}))))
