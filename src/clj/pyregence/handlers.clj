(ns pyregence.handler
  (:require [pyregence.authentication :refer [has-match-drop-access? is-admin?]]
            [ring.util.codec          :refer [url-encode]]
            [ring.util.response       :refer [redirect]]
            [triangulum.response      :refer [no-cross-traffic?]]
            [triangulum.views         :refer [render-page]]))

(def not-found-handler (render-page "/not-found"))

(defn redirect-handler [{:keys [session query-string uri] :as _request}]
  (let [full-url (url-encode (str uri (when query-string (str "?" query-string))))]
    (if (:userId session)
      (redirect (str "/home?flash_message=You do not have permission to access "
                     full-url))
      (redirect (str "/login?returnurl="
                     full-url
                     "&flash_message=You must login to see "
                     full-url)))))

(defn route-authenticator [{:keys [session headers] :as _request} auth-type]
  (let [user-id (:userId session -1)]
    (condp = auth-type
      :admin      (is-admin? user-id)
      :match-drop (has-match-drop-access? user-id)
      :no-cross   (no-cross-traffic? headers)
      :user       (pos? user-id)
      true)))
