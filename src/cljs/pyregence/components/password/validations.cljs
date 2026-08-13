(ns pyregence.components.password.validations
  "Live password-policy UI derived from the single source of truth in
   `pyregence.validation/password-steps` -- the same conditions the backend
   enforces, so the checklist can never drift from what the server accepts."
  (:require
   [clojure.string                 :as str]
   [pyregence.components.svg-icons :as svg]
   [pyregence.validation           :as v]))

(defn ->toast
  "Returns a vector of toast-ready messages describing every password-policy
   condition the given password fails, or nil when it passes (or is blank --
   blankness is reported by the form's required-fields check instead)."
  [password]
  (when-not (str/blank? password)
    (v/errors-for "Your password" v/password-steps password)))

(defn ->cmpt
  "Renders the live checklist of password-policy conditions, each marked with
   a check or an x as the user types."
  [password]
  (when-not (str/blank? password)
    [:div {:style {:padding "20px" :color "black"}}
     [:h6 "Your password:"]
     [:<>
      (for [{:keys [explain valid?]} (v/report v/password-steps password)]
        ^{:key explain}
        [:div {:style {:display         "grid"
                       :justify-content "start"
                       :grid-auto-flow  "column"
                       :height          "25px"}}
         [(if valid? svg/check-mark svg/x-mark-small-red)] [:p explain]])]]))
