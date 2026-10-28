(ns pyregence.weather-stations.ca.xml
  (:import  [java.time LocalDate ZoneOffset]
            [java.time.format DateTimeFormatter])
  (:require [clj-http.client    :as client]
            [clojure.data.xml   :as xml]
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

(defn partner-folder-path
  "The folder holding a partner station's XML files for a date."
  [date today network folder]
  (str (network-path date today network) folder "/"))

(defn latest-file-path
  "The latest XML of a non-partner station, from its REST iata_id and auto_man."
  [iata-id auto-man]
  (str "/today/observations/swob-ml/latest/" iata-id "-" auto-man "-swob.xml"))

(defn pick-newest-xml
  "The newest XML file in a station folder listing. File names start with the
  observation time in every naming scheme seen (2026-10-27-1800-ab-maf-ala-…,
  20261027T2340Z_DND-CCG_…), so the newest sorts last."
  [files]
  (->> files
       (filter #(str/ends-with? % ".xml"))
       sort
       last))

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
  redirect), making up to attempts requests when they don't complete.
  Returns {:listing} or {:status}/{:error} for a failed request."
  [path attempts]
  (loop [attempt 1]
    (let [{:keys [status body error]} (fetch-dd! path)]
      (cond
        (= 200 status)                   {:listing (parse-listing body)}
        (and error (< attempt attempts)) (recur (inc attempt))
        error                            {:error error}
        :else                            {:status status}))))

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
        networks      (let [{:keys [listing status error]} (fetch-listing! partners-path 2)]
                        (or (:dirs listing)
                            (throw (ex-info (str "Listing " partners-path " failed: " (or error status)) {}))))]
    (reduce (fn [folder->networks network]
              (Thread/sleep listing-pause-ms)
              (let [path                           (network-path date today network)
                    {:keys [listing status error]} (fetch-listing! path 2)]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Observations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-swob-elements
  "Maps the name of each <element name=… uom=… value=…/> in a SWOB XML
  document to {:value :uom}, eg {\"air_temp\" {:value \"2.6\" :uom \"°C\"}}.
  Values stay strings. Throws when the XML can't be parsed."
  [xml-string]
  ;; Don't resolve external entities: the XML comes from another server.
  (let [document (xml/parse-str xml-string :supporting-external-entities false)]
    ;; xml/parse-str is lazy; into realizes it here, so parse errors surface
    ;; in this call.
    (into {}
          (for [{:keys [tag attrs]} (xml-seq document)
                :when (= :element tag)]
            [(:name attrs) {:value (:value attrs) :uom (:uom attrs)}]))))

(defn- find-partner-xml-path!
  "The path of the newest XML in a partner station's folder: today's, or
  yesterday's when today's is missing or has no XML yet (just after 00:00
  UTC). One listing request per date tried. Returns {:path} or {:status}."
  [{:keys [network folder]}]
  (if-not folder
    {:status "xml=no folder in the index"}
    (let [today     (get-utc-date 0)
          yesterday (get-utc-date 1)]
      (or (some (fn [date]
                  (let [folder-path (partner-folder-path date today network folder)
                        newest      (some-> (fetch-listing! folder-path 1) :listing :files pick-newest-xml)]
                    (when newest
                      {:path (str folder-path newest)})))
                [today yesterday])
          {:status "xml=no file in today's or yesterday's folder"}))))

(defn fetch-station-elements!
  "Fetches the newest SWOB XML of a CA station and parses it. msc-id is the
  station's msc_id, station its details from
  pyregence.weather-stations.ca/fetch-stations!, and partner-folder its entry
  in the partner folder index (nil when it has none). Returns a map of:
  - :elements, from parse-swob-elements, or nil when there's no usable XML
  - :status, what happened, for the diagnostic headers
  - :path, the XML's path on the server, when one was found
  The XML's msc_id must be the station's: same-name folders and co-located
  stations would otherwise give another station's observation."
  [msc-id {:keys [partner? iata-id auto-man]} partner-folder]
  (let [{:keys [path status]} (if partner?
                                (find-partner-xml-path! partner-folder)
                                {:path (latest-file-path iata-id auto-man)})]
    (if-not path
      {:status status}
      (let [{:keys [status body error]} (fetch-dd! path)
            parsed                      (when (= 200 status)
                                          (try
                                            {:elements (parse-swob-elements body)}
                                            (catch Exception e
                                              {:problem (str "unreadable: " (ex-message e))})))
            xml-msc-id                  (get-in parsed [:elements "msc_id" :value])
            problem                     (cond
                                          ;; The request failed; status tells why
                                          (nil? parsed)            nil
                                          (:problem parsed)        (:problem parsed)
                                          (not= msc-id xml-msc-id) (str "msc_id is " xml-msc-id))]
        (when problem
          (log-str "WARN: XML for CA station " msc-id " at " path ": " problem))
        {:elements (when-not problem (:elements parsed))
         :status   (str "xml=" (or status error) (when problem (str " (" problem ")")))
         :path     path}))))
