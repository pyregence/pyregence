(ns pyregence.session-cookie)

(defn expire
  "Replace the stored cookie with an empty session and expire it immediately."
  [response]
  (assoc response
         :session nil
         :session-cookie-attrs {:max-age 0}))
