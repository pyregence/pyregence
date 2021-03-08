(ns pyregence.sockets
  (:import (java.io BufferedReader)
           (java.net Socket ServerSocket))
  (:require [clojure.java.io :as io]
            [clojure.string  :as s]
            [pyregence.logging :refer [log log-str]]))

;;=================================
;; Client Socket
;;=================================

(defn send-to-server! [host port message]
  (with-open [socket (Socket. host port)]
    (doto (io/writer socket)
      (.write (-> message
                  (s/trim-newline)
                  (str "\n")))
      (.flush))
    (.shutdownOutput socket)))

#_(send-to-server! "wx.pyregence.org"
                   31337
                   "{\"fireName\": \"md-1000\", \"ignitionTime\": \"2021-01-03 16:00 PST\", \"lon\": -117.5, \"lat\": 33.8, \"westBuffer\": 24000, \"southBuffer\": 24000, \"eastBuffer\": 24000, \"northBuffer\": 24000, \"addToActiveFires\": \"yes\", \"scpInputDeck\": \"yes\", \"responseHost\": \"localhost\", \"responsePort\": 31337 }")

;;=================================
;; Server Socket
;;=================================

(defonce ^:private global-server-thread (atom nil))
(defonce ^:private global-server-socket (atom nil))

(defn- read-socket! [^Socket socket]
  (.readLine ^BufferedReader (io/reader socket)))

(defn- accept-connections! [^ServerSocket server-socket handler]
  (while @global-server-thread
    (try
      (let [socket (.accept server-socket)]
        (try
          (->> (read-socket! socket)
               (handler))
          (catch Exception e
            (log-str "Server error: " e))
          (finally (.close socket))))
      (catch Exception _))))

(defn stop-socket-server! []
  (if @global-server-thread
    (do
      (reset! global-server-thread nil)
      (when @global-server-socket
        (.close ^ServerSocket @global-server-socket)
        (reset! global-server-socket nil))
      (log "Server stopped."))
    (log "Server is not running.")))

(defn start-socket-server! [port handler]
  (log "Starting socket server.")
  (if @global-server-thread
    (log "Server is already running.")
    (reset! global-server-thread
            (future
              (try
                (with-open [server-socket (ServerSocket. port)]
                  (reset! global-server-socket server-socket)
                  (accept-connections! server-socket handler))
                (catch Exception e
                  (log-str "Error creating server socket: " e)
                  (stop-socket-server!)))))))

#_(start-socket-server! 31337 (fn [msg] :do-something))
