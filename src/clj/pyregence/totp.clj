(ns pyregence.totp
  (:require [one-time.core  :as ot]
            [one-time.uri   :as uri])
  (:import [java.security SecureRandom]))

(defn generate-secret
  "Generate a new TOTP secret key"
  []
  (ot/generate-secret-key))

(defn generate-totp-uri
  "Generate a TOTP URI for QR code generation.
  Returns a URI string like: otpauth://totp/PyreCast:user@example.com?secret=..."
  [user-email secret]
  (uri/totp-uri {:label "PyreCast"
                 :user user-email
                 :secret secret}))

(defn validate-totp-code
  "Validate a TOTP code against a secret.
  Returns true if valid, false otherwise."
  [secret code]
  (try
    (ot/is-valid-totp-token? (Long/parseLong code) secret)
    (catch NumberFormatException _
      false)))

(defn generate-backup-codes
  "Generate n random backup codes.
  Each code is 8 characters, alphanumeric."
  [n]
  (let [chars    "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        random   (SecureRandom.)
        gen-code (fn []
                   (->> (repeatedly 8 #(.charAt chars (.nextInt random (count chars))))
                        (apply str)))]
    (vec (repeatedly n gen-code))))

(defn get-current-totp-code
  "Get the current TOTP code for a secret (useful for testing)"
  [secret]
  (ot/get-totp-token secret))
