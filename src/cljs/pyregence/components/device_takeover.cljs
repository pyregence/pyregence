(ns pyregence.components.device-takeover
  (:require [clojure.core.async             :refer [<! go]]
            [pyregence.components.buttons   :as buttons]
            [pyregence.components.messaging :refer [toast-message!]]
            [pyregence.components.utils     :as utils]
            [pyregence.device-session       :as device-session]
            [pyregence.utils.async-utils    :as u-async]))

(def message
  "PyreCast is being used in another device. Would you like to use it here instead?")

(defn prompt
  "The explicit transfer decision shown only after this device has authenticated."
  [pending? on-success]
  [utils/card
   {:title "Use PyreCast here?"
    :children
    [:<>
     [:p {:data-testid "device-takeover-message"} message]
     [buttons/primary
      {:text "Use it here"
       :disabled? @pending?
       :on-click
       #(go
          (reset! pending? true)
          (let [{:keys [success status]}
                (<! (device-session/serialized!
                     (fn [] (u-async/call-clj-async! "confirm-login-takeover"))))]
            (if success
              (on-success)
              (do
                (toast-message!
                 (if (= status 409)
                   "The active login changed. Please sign in again."
                   "That device transfer could not be completed. Please sign in again."))
                (reset! pending? false)))))}]]}])
