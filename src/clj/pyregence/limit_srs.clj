(ns pyregence.limit-srs
  (:require [clojure.data.json :as j]
            [clojure.string :as s]
            [clojure.walk :refer [postwalk]]
            [clojure.set :as set]))

(defn s->epsg-code
  [s]
  (Integer/parseInt
   (clojure.string/split s #":")))

(defn response->codes
 "Extracts codes any"
  [forms]
  (let [codes (atom #{})]
    (postwalk
     (fn [form]
       (when (and (string? form)
                  (clojure.string/includes? form "EPSG:"))
         (swap! codes conj
                (-> form
                    s->epsg-code)))
       form)
     forms)
    @codes))

(defn get!*
  [{:keys [href] :as m}]
  (client/get href m))

(def get! (memoize get!*))

(defn read-response
  [response]
  (-> response :body (j/read-str  :key-fn keyword)))

(defn request->response!
  [request]
  (-> request
      (select-keys [:basic-auth :href])
      get!
      read-response))

(defn geoserver-info->codes
  [{:keys [name] :as gs}]
  (->> [#(->> % :layers :layer (map :href))
        #(->> % :layer :resource :href vector)
        response->codes]
       (reduce
        (fn [hrefs response->href]
          (->> hrefs
               (pmap (comp
                      response->href
                      request->response!
                      #(assoc gs :href %)))
               (apply concat)))
        [(str "https://" name ".pyregence.org/geoserver/rest/layers.json")])
       set))

(comment
  (->> [] ;; replace with geoserver info
       (reduce
        (fn [codes gs]
          (set/union codes (geoserver-info->codes gs)))
        #{})))
