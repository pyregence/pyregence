(ns pyregence.utils.browser-utils
  (:require [cljs.reader    :as edn]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Browser Session
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def session-key "pyregence")

(defn- save-session-storage! [data]
  (.setItem (.-sessionStorage js/window) session-key (pr-str data)))

(defn get-session-storage
  "Gets the pyregence session storage data."
  []
  (edn/read-string (.getItem (.-sessionStorage js/window) session-key)))

(defn set-session-storage!
  "Sets the pyregence session storage given data to store."
  [data]
  (save-session-storage! (merge (get-session-storage) data)))

(defn remove-session-storage!
  "Removes the specified pyregence session storage data given keywords."
  [& keys]
  (let [data (get-session-storage)]
    (save-session-storage! (apply dissoc data keys))))

(defn clear-session-storage!
  "Clears the pyregence session storage data."
  []
  (save-session-storage! {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Local Storage
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- save-local-storage! [data]
  (.setItem (.-localStorage js/window) session-key (pr-str data)))

(defn get-local-storage
  "Gets the pyregence local storage data."
  []
  (edn/read-string (.getItem (.-localStorage js/window) session-key)))

(defn set-local-storage!
  "Sets the pyregence local storage given data to store."
  [data]
  (save-local-storage! (merge (get-session-storage) data)))

(defn remove-local-storage!
  "Removes the specified pyregence local storage data given keywords."
  [& keys]
  (let [data (get-local-storage)]
    (save-local-storage! (apply dissoc data keys))))

(defn clear-local-storage!
  "Clears the pyregence local storage data."
  []
  (save-local-storage! {}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utility Functions - Browser Management
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private narrow-window-px
  "Below this width a page lays itself out for a phone.

   One copy of the number. Six pages each carried their own, which is six places
   to change it and six chances to change five of them."
  800.0)

(defn narrow-window?
  "Whether the window is narrow enough that a page should lay itself out for a
   phone. A question about the room available, not about the device: a desktop
   window dragged narrow answers yes, and should."
  []
  (> narrow-window-px (.-innerWidth js/window)))

(defn scroll-to-top!
  "Put the page back at the top of itself."
  []
  (.scrollTo js/window 0 0))

(defn after-the-layout-settles!
  "Call F once the browser has finished laying the page out.

   The browser offers no event for \"you have finished reflowing\", so the idiom
   is a short timer and the number is folklore. Named so that a caller can say
   what it wants -- to measure something that has just moved -- without also
   having to pick a delay."
  [f]
  (js/setTimeout f 50))

(defn when-the-window-changes-shape!
  "Call F now, and again whenever the window is resized or a touch ends -- the
   two events that mean the page has a different amount of room than it did.

   Which DOM events those are is the browser's business and not a page's: a
   component registering them itself is a component that has to name
   \"touchend\" to say \"the layout may have moved\"."
  [f]
  (.addEventListener js/window "resize"   f)
  (.addEventListener js/window "touchend" f)
  (f))

(defn url-param
  "The value the current URL carries for query parameter NAME, or nil where it
   carries none.

   Here rather than at each page that wants one, because `js/URLSearchParams` is
   the browser's abstraction and not PyreCast's: a page component asking it
   directly is a page component that has to know about `js/location`, a
   constructor, and a `.get`, to answer a question that is one word long. Three
   other pages still open it inline (register, setup-2fa, disable-2fa); they
   want moving here too, and that is not this ticket."
  [param-name]
  (-> js/location
      (.-search)
      (js/URLSearchParams.)
      (.get param-name)))

(defn jump-to-url!
  "Redirects the current window to the given URL."
  ([url]
   (let [origin  (.-origin (.-location js/window))
         cur-url (str/replace (.-href (.-location js/window)) origin "")]
     (when-not (= cur-url url) (set! (.-location js/window) url))))
  ([url window-name]
   (if window-name
     (.open js/window url window-name)
     (jump-to-url! url))))

(defn copy-to-clipboard!
  "Copies text to the system clipboard. Returns true if successful, false otherwise."
  [text]
  (let [textarea (.createElement js/document "textarea")]
    (set! (.-value textarea) text)
    (.appendChild js/document.body textarea)
    (.select textarea)
    (let [success (.execCommand js/document "copy")]
      (.removeChild js/document.body textarea)
      success)))

(defn download-backup-codes!
  "Downloads backup codes as a text file"
  [codes]
  (let [content (str "PyreCast 2FA Backup Codes\n"
                     "Generated: " (.toLocaleDateString (js/Date.)) "\n\n"
                     (str/join "\n" codes) "\n\n"
                     "Keep these codes safe. Each can only be used once.")
        blob (js/Blob. #js [content] #js {:type "text/plain"})
        url (.createObjectURL js/URL blob)
        a (.createElement js/document "a")]
    (set! (.-href a) url)
    (set! (.-download a) "pyrecast-backup-codes.txt")
    (.click a)
    (.revokeObjectURL js/URL url)))
