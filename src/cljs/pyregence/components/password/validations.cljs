(ns pyregence.components.password.validations
  (:require
   [clojure.string                 :as str]
   [pyregence.components.svg-icons :as svg]))

(defn- f
  [password]
  [{:validation/text   "One number."
    :validation/valid? (boolean (re-find #"\d" password))}
   {:validation/text   "One lowercase."
    :validation/valid? (boolean (re-find #"[a-z]" password))}
   {:validation/text   "One uppercase."
    :validation/valid? (boolean (re-find #"[A-Z]" password))}
   {:validation/text   "12 characters."
    :validation/valid? (<= 12 (count password))}
   {:validation/text   "At most 64 characters."
    :validation/valid? (<= (count password) 64)}])

(defn ->toast!
  [password]
  (let [validations (f password)]
    (when-not (every? :validation/valid? validations)
      (str "Your password must have "
           (str/lower-case
            (some
             (fn [{:validation/keys [text valid?]}]
               (when (false? valid?) text))
             validations))))))

(defn ->cmpt!
  [password]
  (when-not (str/blank? password)
    [:div {:style {:padding "20px" :color "black"}}
     [:h6 "Your password must have:"]
     [:<>
      (for  [{:validation/keys [text valid?]} (f password)]
        [:div {:style {:display         "grid"
                       :justify-content "start"
                       :grid-auto-flow  "column"
                       :height          "25px"}}
         [(if valid? svg/check-mark svg/x-mark-small-red)] [:p text]])]]))
