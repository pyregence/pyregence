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
