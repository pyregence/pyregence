(ns pyregence.components.help
  (:require [pyregence.utils :as u]
            [pyregence.components.messaging :refer [set-message-box-content!]]))

;;; Help Dialogs

(def help-dialogs {:terrain {:title "3D Terrain Enabled"
                             :body  "You have enabled 3D Terrain. Click and drag using your right mouse button to tilt or rotate the map."}})

;;; Session Helpers

(defn- seen-help! [dialog]
  (-> (u/get-session-storage)
      (update-in [:help] merge {dialog true})
      (u/set-session-storage!)))

(defn- seen-help? [dialog]
  (get-in (u/get-session-storage) [:help dialog]))

;;; Public Functions

(defn show-help!
  [dialog & [always-show]]
  {:pre [((-> help-dialogs keys set) dialog)]}
  (when-not (or always-show (seen-help? dialog))
    (seen-help! dialog)
    (set-message-box-content! (merge (dialog help-dialogs)
                                     {:mode :close}))))
