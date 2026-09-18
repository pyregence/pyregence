(ns pyregence.session-cookie)

(defn expire
  "Replace the stored cookie with an empty session and expire it immediately."
  [response]
  (assoc response
         ;; Ring's cookie store writes this anonymous value before applying the
         ;; expiry attributes.  The overwrite is intentional: if a client or
         ;; local test browser ignores cookie expiry, it still cannot retain the
         ;; authenticated claims from the ended generation.
         :session {}
         :session-cookie-attrs {:max-age 0}))
