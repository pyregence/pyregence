(ns pyregence.components.settings.email)

(def email-domain-regex
  "An organization's email-domain umbrella: a leading @ followed by a domain,
   e.g. @example.com. Users are grouped under an org by dropping the substring
   before the @ in their email address and matching the remainder against this."
  #"@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}")

(defn valid-email-domain? [domain]
  (boolean (re-matches email-domain-regex domain)))

(comment
  ;; --- Orgs always start with an @ ---
  (valid-email-domain? "@example.com")      ;; => true
  (valid-email-domain? "@sig-gis.com")      ;; => true
  (valid-email-domain? "@a.co")             ;; => true

  ;; --- Missing the leading @ is no longer a valid org domain ---
  (valid-email-domain? "example.com")       ;; => false
  (valid-email-domain? "sig-gis.com")       ;; => false

  ;; --- A full email is not an org domain: drop the substring before @ first ---
  (valid-email-domain? "alice@example.com") ;; => false

  ;; --- Degenerate inputs ---
  (valid-email-domain? "@")                 ;; => false
  (valid-email-domain? "@example")          ;; => false  ; no TLD
  (valid-email-domain? "")                  ;; => false
  :rcf)
