(ns pyregence.components.settings.email)

(def email-domain-regex
  #"[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")

(defn valid-email-domain? [domain]
  (boolean (re-matches email-domain-regex domain)))
