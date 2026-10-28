(ns pyregence.weather-stations.ca.xml
  (:import  [java.time LocalDate ZoneOffset]
            [java.time.format DateTimeFormatter])
  (:require [clj-http.client    :as client]
            [clojure.string     :as str]
            [triangulum.logging :refer [log-str]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Canadian weather station observations from SWOB XML files
;; Server: https://dd.weather.gc.ca, an Apache server with directory listings.
;;
;; Non-partner stations: today/observations/swob-ml/latest/<iata_id>-<auto_man>-swob.xml
;; Partner stations:     <root>/partners/<network>/<YYYYMMDD>/<folder>/<files>
;;   where <root> is today/observations/swob-ml for the current UTC date and
;;   <YYYYMMDD>/WXO-DD/observations/swob-ml for past dates (kept ~30 days).
;;
;; The REST station records don't say which network and folder a partner
;; station uses, so an index built from the directory listings maps stations
;; to them. scratchpad/ca-xml-coverage.bb measured the rules below: they map
;; about 93% of the partner stations, and the rest had no XML on the server.
;;
;; The server throttles bursts (about 20% timeouts with 8 concurrent
;; requests), so requests go one at a time, with short timeouts.
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private dd-url "https://dd.weather.gc.ca")

(def ^:private http-timeouts {:connection-timeout 5000
                              :socket-timeout     10000})

;; Pause between the index's listing requests, so they don't burst
(def ^:private listing-pause-ms 250)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Paths
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private ^DateTimeFormatter yyyymmdd (DateTimeFormatter/ofPattern "yyyyMMdd"))

(defn- get-utc-date
  "The UTC date days-ago days before today, as YYYYMMDD."
  [days-ago]
  (.format (.minusDays (LocalDate/now ZoneOffset/UTC) days-ago) yyyymmdd))

(defn swob-root-path
  "The SWOB root folder for a date (YYYYMMDD). today/ only serves the current
  UTC date; past dates live under <date>/WXO-DD/."
  [date today]
  (if (= date today)
    "/today/observations/swob-ml/"
    (str "/" date "/WXO-DD/observations/swob-ml/")))

(defn network-path
  "The folder holding a partner network's station folders for a date."
  [date today network]
  (str (swob-root-path date today) "partners/" network "/" date "/"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Requests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch-dd!
  "GETs a path on the XML server. Returns {:status :body}, or {:error} when
  the request doesn't complete (timeouts included)."
  [path]
  (try
    (select-keys (client/get (str dd-url path) (merge http-timeouts {:throw-exceptions false}))
                 [:status :body])
    (catch Exception e
      {:error (str (.getSimpleName (class e)) ": " (ex-message e))})))

(defn parse-listing
  "The entries of an Apache directory listing, as {:dirs [...] :files [...]},
  directory names without their trailing slash. Skips the sort links (?C=...)
  and absolute links (the parent folder)."
  [html]
  (let [hrefs (->> (re-seq #"href=\"([^\"]+)\"" html)
                   (map second)
                   (remove #(or (str/starts-with? % "?")
                                (str/starts-with? % "/")
                                (str/starts-with? % "../"))))]
    {:dirs  (->> hrefs
                 (filter #(str/ends-with? % "/"))
                 (mapv #(subs % 0 (dec (count %)))))
     :files (->> hrefs
                 (remove #(str/ends-with? % "/"))
                 vec)}))

(defn- fetch-listing!
  "The listing at path (which must end in /, or the server answers with a
  redirect), retrying once when the request doesn't complete. Returns
  {:listing} or {:status}/{:error} for a failed request."
  [path]
  (loop [attempt 1]
    (let [{:keys [status body error]} (fetch-dd! path)]
      (cond
        (= 200 status)            {:listing (parse-listing body)}
        (and error (= 1 attempt)) (recur 2)
        error                     {:error error}
        :else                     {:status status}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Partner folder index
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private partner-folder-rules
  "Each rule turns a partner station (msc_id and details) into a candidate
  folder name; the first rule whose folder exists wins. Rule 1 matches 99% of
  the matched stations."
  [;; AB-MAF_ALA, iata_id ALA -> ala
   (fn [_ {:keys [iata-id]}] (some-> iata-id str/lower-case))
   ;; DND lighthouses: iata_id TRIAL IS -> trial_is
   (fn [_ {:keys [iata-id]}] (some-> iata-id str/lower-case (str/replace " " "_")))
   ;; POM_quai_42 -> pom_quai_42, YAA_YAAFRA -> yaa_yaafra
   (fn [msc-id _] (str/lower-case msc-id))])

(defn match-partner-folder
  "The first folder a rule maps the station to that exists on the server, as
  {:folder :networks}, or nil. folder->networks maps every station folder
  name to the networks it appears in."
  [folder->networks msc-id station]
  (some (fn [rule]
          (let [folder (rule msc-id station)]
            (when-let [networks (get folder->networks folder)]
              {:folder folder :networks networks})))
        partner-folder-rules))

(defn msc-id->provider
  "The part of an msc_id before its last _ (BC_ENV-AQ_110 -> BC_ENV-AQ),
  which groups the stations of a data provider. Lighthouse msc_ids have no _
  and form one group."
  [msc-id]
  (if-let [i (str/last-index-of msc-id "_")]
    (subs msc-id 0 i)
    ""))

(defn learn-provider-networks
  "Maps each provider to its networks: those holding at least 10% of the
  provider's single-network matches. matches maps msc_id to the result of
  match-partner-folder. Almost every provider uses one network (NWT_ENR
  uses two), so this resolves folder names found in several networks."
  [matches]
  (let [provider->network-counts (->> matches
                                      (filter (fn [[_ {:keys [networks]}]] (= 1 (count networks))))
                                      (group-by (fn [[msc-id _]] (msc-id->provider msc-id)))
                                      (map (fn [[provider provider-matches]]
                                             [provider (frequencies (map (fn [[_ {:keys [networks]}]] (first networks))
                                                                         provider-matches))])))]
    (into {}
          (for [[provider network-counts] provider->network-counts
                :let [total (reduce + (vals network-counts))]]
            [provider (set (for [[network n] network-counts
                                 :when (>= n (* 0.1 total))]
                             network))]))))

(defn build-partner-folders
  "Maps the msc_id of each partner station to its {:network :folder}.
  Narrows each match to the networks of the station's provider, and leaves
  out stations that still match several networks, or only networks outside
  their provider's (a same-name folder of another network, while the
  station's own folder is missing)."
  [stations folder->networks]
  (let [matches            (into {}
                                 (for [[msc-id station] stations
                                       :when (:partner? station)
                                       :let [match (match-partner-folder folder->networks msc-id station)]
                                       :when match]
                                   [msc-id match]))
        provider->networks (learn-provider-networks matches)]
    (into {}
          (for [[msc-id {:keys [folder networks]}] matches
                :let [provider-networks (get provider->networks (msc-id->provider msc-id) #{})
                      narrowed          (filter provider-networks networks)]
                :when (= 1 (count narrowed))]
            [msc-id {:network (first narrowed) :folder folder}]))))

(defn- fetch-folder->networks!
  "Maps every partner station folder name to the networks it appears in, from
  yesterday's listings: today's fill up during the day, yesterday's are
  complete, and folder names don't change from day to day. One request per
  network (about 30), one at a time. Throws when a listing fails."
  []
  (let [date          (get-utc-date 1)
        today         (get-utc-date 0)
        partners-path (str (swob-root-path date today) "partners/")
        networks      (let [{:keys [listing status error]} (fetch-listing! partners-path)]
                        (or (:dirs listing)
                            (throw (ex-info (str "Listing " partners-path " failed: " (or error status)) {}))))]
    (reduce (fn [folder->networks network]
              (Thread/sleep listing-pause-ms)
              (let [path                           (network-path date today network)
                    {:keys [listing status error]} (fetch-listing! path)]
                (when-not listing
                  (throw (ex-info (str "Listing " path " failed: " (or error status)) {})))
                (reduce (fn [acc folder]
                          (update acc folder (fnil conj #{}) network))
                        folder->networks
                        (:dirs listing))))
            {}
            networks)))

(defn fetch-partner-folders!
  "Builds the partner folder index for the CA stations (msc_id to station
  details, from pyregence.weather-stations.ca/fetch-stations!). Returns a map
  of :partner-folders, msc_id to {:network :folder}. Throws when the station
  list isn't loaded yet, so the refresh loop retries later."
  [stations]
  (when (empty? stations)
    (throw (ex-info "The CA station list isn't loaded yet" {})))
  (let [folder->networks (fetch-folder->networks!)
        partner-folders  (build-partner-folders stations folder->networks)]
    (log-str "Weather stations: XML index maps " (count partner-folders)
             " of " (count (filter (comp :partner? val) stations))
             " CA partner stations to a folder, out of " (count folder->networks) " folders")
    {:partner-folders partner-folders}))
