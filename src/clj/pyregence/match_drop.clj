(ns pyregence.match-drop
  (:require [clojure.data.json :as json]
            [pyregence.sockets :refer [send-to-server!]]))

(def job-queue (atom {}))

(defn initiate-md! [params]
  (let [job-id   (int (rand 100000000))
        request  (merge params
                        {:job-id              job-id
                         :west-buffer         24000
                         :north-buffer        24000
                         :add-to-active-fires "yes"
                         :south-buffer        24000
                         :fire-name           (str "match-drop-" job-id)
                         :east-buffer         24000})]
    (swap! job-queue
           #(assoc %
                   job-id
                   {:stage :initiated
                    :request request}))
    #_(send-to-server! "wx.pyregence.org" 31337 (json/write-str request))
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
  (let [incoming (json/read-str msg :key-fn keyword)
        job-id   (:job-id incoming)]
    (process-stage! (get-in @job-queue [job-id :stage])
                    job-id)
    (println @job-queue)))

(defn process-message [msg]
  (do-work msg))
