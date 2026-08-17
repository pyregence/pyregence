(ns pyregence.validation
  "Composable string normalizers built from conditionals.

   A *step* is a small map describing one link in a normalization pipeline.
   Steps come in two flavors:

   - a **check** validates the current value and contributes a human-readable
     explanation when it fails (`:valid?` + `:explain`);
   - a **transform** rewrites the current value on its way through
     (`:normalize`), e.g. trimming or lower-casing.

   `normalizer` composes steps into a single function of one string. On
   success it returns the normalized, sanitized string. On failure it throws
   an `ex-info` whose `ex-data` carries *every* failed condition's
   explanation — checks are applied applicatively (all of them run and all
   failures are collected) rather than monadically (stop at the first).
   The one exception is a step marked as a *gate* (see `gate`): when a gate
   fails, later checks are skipped, because 'Email must not be blank'
   followed by 'Email must be a valid address' is noise, not information.

   `validate-all!` lifts the same idea across several fields at once,
   throwing a single aggregate exception whose data is ready to serialize
   to a frontend (see `invalid?` and `ex->data`).

   Because this namespace is `.cljc`, the exact same field rules run on the
   JVM (as the authoritative server-side validation) and in the browser (for
   instant feedback and checklist UIs via `report`)."
  (:require [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Step Constructors
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn check
  "Builds a validation step from an explanation and a predicate.

   `explain` is either a string ('must contain at least one number (0-9)')
   or a function of the offending value returning such a string — the latter
   lets a failure message quote the exact part of the input that was invalid.
   `pred` receives the current (possibly already normalized) string value."
  [explain pred]
  {:explain explain
   :valid?  pred})

(defn gate
  "Like `check`, but when a gate fails, no further steps are evaluated and
   only the gate's explanation is reported. Use for preconditions that make
   every later message redundant (e.g. `required`)."
  [explain pred]
  (assoc (check explain pred) :gate? true))

(defn transform
  "Builds a normalization step from a function of string -> string.
   Transforms never fail; they sanitize the value for subsequent steps and
   for the final returned result."
  [f]
  {:normalize f})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Regex Introspection
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn unicode-pattern
  "Builds a regex from `source` with Unicode property (\\p{...}) support on
   both platforms. The JVM understands properties natively; JavaScript needs
   the 'u' flag, which ClojureScript regex *literals* cannot carry -- hence
   runtime construction. Use for classes like \"[\\\\p{L}\\\\p{M}]\" so names
   such as José, Björk, or 习近平 validate correctly."
  [source]
  #?(:clj  (re-pattern source)
     :cljs (js/RegExp. source "u")))

(defn- re-source
  "Returns the source text of a regex literal on either platform."
  [re]
  #?(:clj  (.pattern ^java.util.regex.Pattern re)
     :cljs (.-source re)))

(defn- char-class-inner
  "Given a character-class regex like #\"[a-zA-Z0-9._-]\", returns the text
   between the brackets, or nil when the pattern is not a bare char class."
  [re]
  (let [src (re-source re)]
    (when (and (str/starts-with? src "[")
               (str/ends-with? src "]"))
      (subs src 1 (dec (count src))))))

(defn invert-char-class
  "Inverts a character-class regex: #\"[a-z]\" becomes #\"[^a-z]\".
   Matching the inversion against an input finds exactly the characters the
   original class does *not* allow — i.e. the invalid part of the input.
   Regex flags (notably JavaScript's 'u' for Unicode properties) survive
   the inversion."
  [re]
  (let [inverted (str "[^" (char-class-inner re) "]")]
    #?(:clj  (re-pattern inverted)
       :cljs (js/RegExp. inverted (.-flags re)))))

(def ^:private known-token->english
  "Recognized character-class tokens, in the order they are peeled from the
   front of a class. Unicode property escapes come first so \"\\p{L}\" is
   never misread as literals; ASCII ranges follow."
  [["\\p{L}"  "letters (any language)"]
   ["\\p{M}"  "accent marks"]
   ["\\p{Nd}" "digits (0-9)"]
   ["a-z"     "lowercase letters (a-z)"]
   ["A-Z"     "uppercase letters (A-Z)"]
   ["0-9"     "digits (0-9)"]])

(defn char-class->english
  "Expands a character-class regex into the sets and ranges of characters it
   supports, in plain English. Understands ASCII ranges and the Unicode
   property escapes \\p{L}, \\p{M}, and \\p{Nd}.

   #\"[a-zA-Z0-9._-]\" => \"lowercase letters (a-z), uppercase letters (A-Z),
   digits (0-9), and the characters . _ -\""
  [re]
  (let [inner    (char-class-inner re)
        ;; Peel off leading known tokens, then treat the rest as literals.
        [tokens rest-str]
        (loop [s inner tokens []]
          (if-let [[t english] (some (fn [[t :as pair]]
                                       (when (str/starts-with? s t) pair))
                                     known-token->english)]
            (recur (subs s (count t)) (conj tokens english))
            [tokens s]))
        raw      (str/replace rest-str "\\" "")
        space?   (str/includes? raw " ")
        literals (str/replace raw " " "")
        parts    (cond-> tokens
                   space?
                   (conj "spaces")
                   (seq literals)
                   (conj (str "the characters "
                              (str/join " " (map str literals)))))]
    (case (count parts)
      0 ""
      1 (first parts)
      (str (str/join ", " (butlast parts)) ", and " (last parts)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Built-in Steps
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def required
  "Gate: the value must be a non-blank string. When this fails nothing else
   is worth saying, so it suppresses subsequent explanations."
  (gate "must not be blank"
        #(not (str/blank? %))))

(def trimmed
  "Transform: strips leading and trailing whitespace."
  (transform str/trim))

(def lower-cased
  "Transform: lower-cases the value (e.g. for case-insensitive identities
   such as email addresses)."
  (transform str/lower-case))

(def unicode-normalized
  "Transform: canonically composes the value (Unicode NFC), so a name typed
   with a combining accent ('José', e + U+0301) is stored — and length-counted
   and matched — identically to its precomposed form ('José', U+00E9)."
  (transform (fn [s]
               #?(:clj  (java.text.Normalizer/normalize
                         s java.text.Normalizer$Form/NFC)
                  :cljs (.normalize s "NFC")))))

(def no-surrounding-whitespace
  "Check (rather than silently fixing): the value must not begin or end with
   whitespace. Prefer `trimmed` when quietly repairing is acceptable; prefer
   this for values like passwords that must never be rewritten."
  (check "must not begin or end with a space"
         #(= % (str/trim %))))

(defn min-length
  "Check: the value must contain at least `n` characters."
  [n]
  (check (str "must be at least " n " characters long")
         #(<= n (count %))))

(defn max-length
  "Check: the value must contain at most `n` characters."
  [n]
  (check (str "must be at most " n " characters long")
         #(<= (count %) n)))

(defn length-between
  "Check: the value's length must fall within [lo, hi], inclusive. Reports
   the actual length on failure."
  [lo hi]
  (check (fn [s]
           (str "must be between " lo " and " hi " characters long"
                " (yours is " (count s) ")"))
         #(<= lo (count %) hi)))

(defn matches
  "Check: the value must match `re` in full. `description` finishes the
   sentence 'must ...', e.g. 'be a valid email address like a@b.com'."
  [re description]
  (check (str "must " description)
         #(boolean (re-matches re %))))

(defn contains-some
  "Check: the value must contain at least one match of `re`. `description`
   names the missing thing, e.g. 'one number (0-9)'."
  [re description]
  (check (str "must contain at least " description)
         #(boolean (re-find re %))))

(defn forbids
  "Check: the value must contain no match of `re`. On failure, quotes the
   offending fragments so the user can see exactly what was rejected."
  [re description]
  (check (fn [s]
           (str "must not contain " description
                " (found: "
                (str/join " " (map #(str "'" % "'") (distinct (re-seq re s))))
                ")"))
         #(not (re-find re %))))

(defn allowed-chars
  "Check built from a character-class regex such as #\"[a-zA-Z0-9._-]\".

   The class is expanded into the sets and ranges of characters it supports
   for the success criteria, and *inverted* on failure to quote precisely
   which characters of the input were invalid."
  [class-re]
  (let [inverted (invert-char-class class-re)]
    (check (fn [s]
             (str "may only contain " (char-class->english class-re)
                  " (invalid: "
                  (str/join " " (map #(str "'" % "'")
                                     (distinct (re-seq inverted s))))
                  ")"))
           #(not (re-find inverted %)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Core Runner
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- explain-str
  "Resolves a step's `:explain` (string or fn of the value) to a string."
  [{:keys [explain]} value]
  (if (fn? explain) (explain value) explain))

(defn run-steps
  "Threads `input` (nil-safe: coerced to a string) through `steps`,
   applying transforms and collecting every failed check's explanation.
   Returns {:value <normalized string> :errors [<explanation> ...]};
   `:errors` is empty when the input is valid."
  [steps input]
  (loop [[{:keys [gate? normalize valid?] :as step} & more] steps
         value  (str input)
         errors []]
    (cond
      (nil? step)
      {:errors errors :value value}

      normalize
      (recur more (normalize value) errors)

      (valid? value)
      (recur more value errors)

      gate?
      {:errors (conj errors (explain-str step value)) :value value}

      :else
      (recur more value (conj errors (explain-str step value))))))

(defn report
  "Runs only the checks (skipping gates) against `input`, after applying any
   transforms, and returns a per-condition breakdown suitable for live
   checklist UIs:

   [{:explain \"must contain at least one number (0-9)\" :valid? false} ...]"
  [steps input]
  (loop [[{:keys [gate? normalize valid?] :as step} & more] steps
         value  (str input)
         result []]
    (cond
      (nil? step) result
      normalize   (recur more (normalize value) result)
      gate?       (recur more value result)
      :else       (recur more value (conj result
                                          {:explain (explain-str step value)
                                           :valid?  (boolean (valid? value))})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Message Assembly & Exceptions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn field-errors->messages
  "Prefixes each of a field's explanations with the field name:
   (\"Email\", [\"must not be blank\"]) => [\"Email must not be blank\"]."
  [field errors]
  (mapv #(str field " " %) errors))

(defn assemble-message
  "Assembles prefixed error messages into one well-formed sentence:
   \"Email must not be blank; Password must be between 12 and 64 characters
   long (yours is 4).\""
  [messages]
  (str (str/join "; " messages) "."))

(defn- throw-invalid!
  "Throws the canonical validation `ex-info`. `field->pairs` is an *ordered*
   sequence of `[field errors]` pairs (field name -> vector of bare
   explanations); message order follows the sequence rather than any map's
   iteration order, which is only insertion-ordered for small maps. The raw
   input is deliberately never attached: it can carry a plaintext password, and
   an ex-info may be logged on the non-validation path."
  [field->pairs]
  (let [messages (into []
                       (mapcat (fn [[field errors]]
                                 (field-errors->messages field errors)))
                       field->pairs)
        message  (assemble-message messages)]
    (throw (ex-info message
                    {::invalid true
                     :fields   (into {} field->pairs)
                     :errors   messages
                     :message  message}))))

(defn invalid?
  "True when `e` is an exception thrown by a normalizer from this namespace,
   i.e. its `ex-data` is safe to serialize back to the user as explanation."
  [e]
  (boolean (::invalid (ex-data e))))

(defn ex->data
  "Extracts the frontend-ready payload from a validation exception:
   {:message <assembled sentence> :errors [<per-condition message> ...]}."
  [e]
  (select-keys (ex-data e) [:message :errors]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Composition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn normalizer
  "Composes `steps` into a normalizer for the field named `field`.

   Returns a function of one string that either returns the normalized,
   sanitized value (transforms applied) or throws an `ex-info` collecting
   the explanation of every failed check (see `invalid?` / `ex->data`)."
  [field steps]
  (fn [input]
    (let [{:keys [errors value]} (run-steps steps input)]
      (if (seq errors)
        (throw-invalid! [[field errors]])
        value))))

(defn optional
  "Wraps a normalizer so that a nil or blank input short-circuits to nil
   instead of being validated. Use for fields that may be omitted but must
   be well-formed when present."
  [normalize-fn]
  (fn [input]
    (when-not (str/blank? (str input))
      (normalize-fn input))))

(defn errors-for
  "Non-throwing convenience for UI pre-validation: returns the prefixed
   error messages for `input` against `steps`, or nil when valid."
  [field steps input]
  (let [{:keys [errors]} (run-steps steps input)]
    (when (seq errors)
      (field-errors->messages field errors))))

(defn validate-all!
  "Validates several fields applicatively: every field is checked, every
   failure is collected, and a single aggregate exception is thrown when
   anything failed — so a user mistyping three things hears about all three
   at once, not one per submission.

   `specs` is a vector of [result-key field-name steps input] tuples.
   Returns {result-key <normalized value> ...} when everything passes."
  [specs]
  (let [runs         (mapv (fn [[k field steps input]]
                             [k field (run-steps steps input)])
                           specs)
        field->pairs (into []
                           (keep (fn [[_ field {:keys [errors]}]]
                                   (when (seq errors) [field errors])))
                           runs)]
    (if (seq field->pairs)
      (throw-invalid! field->pairs)
      (into {} (map (fn [[k _ {:keys [value]}]] [k value])) runs))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; PyreCast Account Fields
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def email-steps
  "Signup email pipeline: sanitize to the canonical lower-cased, trimmed
   form the DB authenticates against, then validate shape and length."
  [required
   trimmed
   lower-cased
   (max-length 254)
   (matches #"[a-z0-9._%+\-]+@[a-z0-9.\-]+\.[a-z]{2,}"
            "be a valid email address like name@example.com")])

(def password-steps
  "Password policy, single-sourced for backend enforcement, the register
   page's live checklist (via `report`), and toasts. Passwords are checked,
   never transformed — silently rewriting a secret is how lockouts happen."
  [required
   (length-between 12 64)
   (contains-some #"\d" "one number (0-9)")
   (contains-some #"[a-z]" "one lowercase letter (a-z)")
   (contains-some #"[A-Z]" "one uppercase letter (A-Z)")
   no-surrounding-whitespace])

(def user-name-class
  "The single source of truth for which characters a display name may contain:
   letters and accent marks from any script, digits, spaces, and . , ' - . Shared
   by `user-name-steps` (which validates) and `sanitize-user-name` (which coerces
   a derived default into this set), so the two can never disagree."
  (unicode-pattern "[\\p{L}\\p{M}\\p{Nd} .,'\\-]"))

(def user-name-steps
  "Display-name pipeline: NFC-normalizes, trims, bounds length, and
   whitelists characters via Unicode properties, so José, Zoë, Xi Jinping,
   习近平, Björk Guðmundsdóttir, and O'Neill-Smythe all validate while the
   whitelist (rather than a blacklist) remains the XSS defense: <, >, quotes,
   ampersands, and friends are simply not expressible, and a failure quotes
   the exact characters that were rejected."
  [unicode-normalized
   trimmed
   (max-length 100)
   (allowed-chars user-name-class)])

(def org-name-steps
  "Organization-name pipeline for marketplace signup; same Unicode whitelist
   strategy as display names, slightly wider ('&', parentheses) for company
   names — but angle brackets and quotes remain impossible ('&' alone cannot
   open a tag)."
  [required
   unicode-normalized
   trimmed
   (max-length 100)
   (allowed-chars (unicode-pattern "[\\p{L}\\p{M}\\p{Nd} .,'()&\\-]"))])

(def normalize-email
  "Email -> canonical email, or throws with every failed condition."
  (normalizer "Email" email-steps))

(def normalize-password
  "Password -> the same password (never rewritten), or throws with every
   failed policy condition."
  (normalizer "Password" password-steps))

(def normalize-user-name
  "Display name -> trimmed display name; blank stays allowed (returns nil)
   because signup does not require a name."
  (optional (normalizer "Name" user-name-steps)))

(defn sanitize-user-name
  "Best-effort coercion of an arbitrary string into the display-name whitelist:
   drops every character `user-name-class` forbids, then trims. Unlike
   `normalize-user-name` this never throws -- it exists to derive a *default*
   name (e.g. from an email's local part, which may contain '_', '+' or '%'
   that a display name may not), not to validate what a user deliberately typed.
   May return \"\" when nothing survives, in which case the name is simply
   omitted downstream."
  [s]
  (str/trim (str/replace (str s) (invert-char-class user-name-class) "")))

(def normalize-org-name
  "Marketplace organization name -> trimmed name, or nil when omitted."
  (optional (normalizer "Organization name" org-name-steps)))
