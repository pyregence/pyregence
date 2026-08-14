(ns pyregence.validation-test
  "Tests for the composable string-normalizer DSL. These complement the
   rich-comment-tests embedded in `pyregence.validation` (run via `bb test`);
   this namespace runs under the cognitect test-runner (`bb test-clj`) and
   needs no database, so it can gate CI cheaply."
  (:require [clojure.string       :as str]
            [clojure.test         :refer [deftest is testing]]
            [pyregence.validation :as v]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- errors-of
  "Runs `f` on `input`, returning the thrown validation errors or ::valid."
  [f input]
  (try
    (f input)
    ::valid
    (catch Exception e
      (if (v/invalid? e)
        (:errors (ex-data e))
        (throw e)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Normalization (the truthy path returns the sanitized string)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest normalizer-returns-sanitized-string
  (testing "transforms run in order and their result is returned"
    (is (= "liquori.alice@example.com"
           (v/normalize-email "  Liquori.Alice@Example.COM "))))
  (testing "transforms feed subsequent checks, so repairable input passes"
    (is (= "aa@bb.co" (v/normalize-email "aa@bb.co "))))
  (testing "passwords are validated but never rewritten"
    (let [password "GoodPassw0rdHere"]
      (is (identical? password (v/normalize-password password))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Applicative error collection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest every-failed-condition-is-collected
  (testing "one bad password reports each violated policy condition"
    (is (= ["Password must be between 12 and 64 characters long (yours is 4)"
            "Password must contain at least one number (0-9)"
            "Password must contain at least one uppercase letter (A-Z)"]
           (errors-of v/normalize-password "shrt"))))
  (testing "a valid password reports nothing"
    (is (= ::valid (errors-of v/normalize-password "GoodPassw0rdHere")))))

(deftest gates-suppress-redundant-messages
  (testing "a blank email says only that it is blank, not that it is misshapen"
    (is (= ["Email must not be blank"] (errors-of v/normalize-email ""))))
  (testing "nil input is handled identically to blank"
    (is (= ["Email must not be blank"] (errors-of v/normalize-email nil)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Regex introspection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest char-class-expansion
  (is (= "lowercase letters (a-z), uppercase letters (A-Z), digits (0-9), and the characters . _ -"
         (v/char-class->english #"[a-zA-Z0-9._-]")))
  (is (= "digits (0-9)" (v/char-class->english #"[0-9]")))
  (testing "Unicode property escapes are expanded, not read as literals"
    (is (= "letters (any language), accent marks, digits (0-9), spaces, and the characters . , ' -"
           (v/char-class->english (v/unicode-pattern "[\\p{L}\\p{M}\\p{Nd} .,'-]"))))))

(deftest inverted-char-class-finds-the-invalid-part
  (is (= "X" (re-find (v/invert-char-class #"[a-z]") "abcXdef")))
  (is (nil? (re-find (v/invert-char-class #"[a-z]") "abcdef"))))

(deftest allowed-chars-quotes-offenders
  (let [normalize (v/normalizer "Name" v/user-name-steps)]
    (testing "XSS-capable characters are rejected and quoted back"
      (is (= ["Name may only contain letters (any language), accent marks, digits (0-9), spaces, and the characters . , ' - (invalid: '<' '>')"]
             (errors-of normalize "Bobby <script>"))))
    (testing "ordinary names normalize cleanly"
      (is (= "Dr. Mothball" (normalize " Dr. Mothball ")))
      (is (= "O'Neill-Smythe, Jr." (normalize "O'Neill-Smythe, Jr."))))))

(deftest international-names-are-first-class
  (let [normalize (v/normalizer "Name" v/user-name-steps)]
    (testing "names from any script pass the Unicode-property whitelist"
      (doseq [person ["josé"
                      "Xi Jinping"
                      "习近平"
                      "Björk Guðmundsdóttir"
                      "Ngô Đình Diệm"
                      "Алексей Навальный"
                      "غادة السمان"
                      "Zoë"]]
        (is (= person (normalize person))
            (str "expected " (pr-str person) " to validate"))))
    (testing "combining accents are canonicalized to precomposed NFC"
      ;; ́ is the combining acute; é is the precomposed é.
      (is (= "Jos\u00e9" (normalize "Jose\u0301")))
      (is (= 4 (count (normalize "Jose\u0301")))))
    (testing "the max-length check counts the NFC form, not the raw input"
      ;; 100 combining pairs = 200 chars raw, 100 after NFC -> valid.
      (is (= 100 (count (normalize (apply str (repeat 100 "e\u0301")))))))
    (testing "markup is rejected regardless of surrounding script"
      (is (vector? (errors-of normalize "习近平<b>")))
      (is (str/includes? (first (errors-of normalize "习近平<b>"))
                         "(invalid: '<' '>')")))
    (testing "organization names admit '&' and parentheses, still not markup"
      (is (= "Müller & Söhne (GmbH)" (v/normalize-org-name " Müller & Söhne (GmbH) ")))
      (is (vector? (errors-of v/normalize-org-name "Müller <script>"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Multi-field aggregation & the wire contract
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest validate-all-aggregates-across-fields
  (testing "failures in several fields raise one exception carrying them all"
    (let [e (try
              (v/validate-all!
               [[:email    "Email"    v/email-steps    "not-an-email"]
                [:password "Password" v/password-steps "shrt"]])
              nil
              (catch Exception e e))]
      (is (v/invalid? e))
      (is (= 4 (count (:errors (ex-data e)))))
      (is (str/starts-with? (:message (ex-data e)) "Email must be a valid"))
      (is (some #(str/starts-with? % "Password must contain")
                (:errors (ex-data e))))))
  (testing "a fully valid submission returns the normalized field map"
    (is (= {:email "aa@bb.co" :password "GoodPassw0rdHere"}
           (v/validate-all!
            [[:email    "Email"    v/email-steps    " aa@bb.co "]
             [:password "Password" v/password-steps "GoodPassw0rdHere"]])))))

(deftest ex->data-is-frontend-ready
  (let [e (try (v/normalize-email "bad") nil (catch Exception e e))]
    (testing "exactly the keys the client toasts, nothing internal"
      (is (= #{:message :errors} (set (keys (v/ex->data e))))))
    (testing "the assembled message is a well-formed sentence"
      (is (= "Email must be a valid email address like name@example.com."
             (:message (v/ex->data e)))))
    (testing "non-validation exceptions are distinguishable"
      (is (not (v/invalid? (ex-info "boom" {})))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Optional fields & UI helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest optional-fields-pass-blank-through
  (is (nil? (v/normalize-org-name nil)))
  (is (nil? (v/normalize-org-name "   ")))
  (is (= "Acme, Inc." (v/normalize-org-name "  Acme, Inc.  ")))
  (is (vector? (errors-of v/normalize-org-name "<Acme>"))))

(deftest report-powers-checklist-uis
  (testing "each non-gate condition reports its text and validity"
    (let [rows (v/report v/password-steps "abc")]
      (is (= [false false true false true] (mapv :valid? rows)))
      (is (every? string? (map :explain rows)))))
  (testing "errors-for is the non-throwing variant for pre-validation"
    (is (nil? (v/errors-for "Email" v/email-steps "aa@bb.co")))
    (is (= ["Email must be a valid email address like name@example.com"]
           (v/errors-for "Email" v/email-steps "nope")))))
