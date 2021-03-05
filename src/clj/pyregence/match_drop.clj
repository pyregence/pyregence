(ns pyregence.match-drop
  (:require [clojure.data.json :as json]
            [pyregence.sockets :refer [send-to-server!]]))

(def job-queue (atom {}))

(defn transform-keys [])

(defn initiate-md! [params]
  (let [job-id  (int (rand 100000000))
        request (merge params
                        {:job-id              job-id
                         :west-buffer         24000
                         :north-buffer        24000
                         :add-to-active-fires "yes"
                         :south-buffer        24000
                         :fire-name           (str "match-drop-" job-id)
                         :east-buffer         24000
                         :host-name           "47.33.29.169"})]
    (swap! job-queue
           #(assoc %
                   job-id
                   {:stage :initiated
                    :request request}))
    (send-to-server! "wx.pyregence.org" 31337
                     "{\"fireName\": \"md-1000\", \"ignitionTime\": \"2021-01-03 16:00 PST\", \"lon\": -117.5, \"lat\": 33.8, \"westBuffer\": 24000, \"southBuffer\": 24000, \"eastBuffer\": 24000, \"northBuffer\": 24000, \"addToActiveFires\": \"yes\", \"scpInputDeck\": \"yes\", \"responseHost\": \"47.33.29.169\", \"responsePort\": 31337 }")
    (println @job-queue)
    job-id))

(defn get-md-status [job-id]
  (get {:initiated "Getting weather data"
        :model     "Running models"
        :geoserver "Registering Layers"}
       (get-in @job-queue [job-id :stage])
       "Finished running"))

(defmulti process-stage! (fn [stage job-id] stage))

(defmethod process-stage! :initiated [_ job-id]
  (swap! job-queue #(assoc-in % [job-id :stage] :model)))

(defmethod process-stage! :model [_ job-id]
  (swap! job-queue #(assoc-in % [job-id :stage] :geoserver)))

(defmethod process-stage! :geoserver [_ job-id]
  (swap! job-queue #(assoc-in % [job-id :stage] :done)))

(defmethod process-stage! :default [_ job-id]
  (swap! job-queue #(dissoc % job-id)))

(defn do-work [msg]
  (println "---------" msg)
  (let [incoming (json/read-str msg :key-fn keyword)
        job-id   (:job-id incoming)]
    (process-stage! (get-in @job-queue [job-id :stage])
                    job-id)
    (println @job-queue)))

(defn process-message [msg]
  (do-work msg))
