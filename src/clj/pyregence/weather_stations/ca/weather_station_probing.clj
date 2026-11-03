(ns pyregence.weather-stations.ca.weather-station-probing
  (:require [clj-http.client                   :as client]
            [clojure.data.csv                  :as csv]
            [clojure.data.json                 :as json]
            [clojure.data.xml                  :as xml]
            [clojure.edn                       :as edn]
            [clojure.java.io                   :as io]
            [clojure.string                    :as str]
            [pyregence.weather-stations.ca.xml :as xml-ca]
            [pyregence.weather-stations.ca     :as ca]))

(comment
  (def output-dir "/home/lima.guest/ca-weather-stations-probing/")
  (def timestamp (.format
                 (java.time.LocalDate/now)
                 (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd")))

  ;; FETCH THE LIST OF STATIONS
  ;; Both partner and non-partner
  ;; Do this only once, when starting tests, then read the saved .edn files
  ;; to continue exploration to avoid hammering the service.
  ;; These fetches just get the data from the API, without processing it.

  ;; The list of core (non-partner) stations returned by the REST API
  (def core-stations (ca/fetch-station-collection! "swob-stations"))
  (spit (str output-dir "/swob-stations-" timestamp  ".edn") core-stations)

  ;; The list of partner stations returned by the REST API
  (def partner-stations (ca/fetch-station-collection! "swob-partner-stations"))
  (spit (str output-dir "/swob-partner-stations-" timestamp  ".edn") partner-stations)

  ;; READ STATION LISTS FROM FILE
  ;; NOTE that this overrides the defs above. This is intentional so that
  ;; they are interchangeable in the code that follows.

  (def read-timestamp "20261102") ;; Pin the timestamp whose edns we want to read
  (def core-stations (edn/read-string (slurp (str output-dir "/swob-stations-" read-timestamp  ".edn"))))
  (def partner-stations (edn/read-string (slurp (str output-dir "/swob-partner-stations-" read-timestamp  ".edn"))))

  ;; COUNT THE NUMBER OF STATIONS IN EACH GROUP
  (def num-core-stations (count core-stations))
  ;; => 938
  (def num-partner-stations (count partner-stations))
  ;; => 2030

  ;; FETCH ALL OBSERVATIONS FOR CORE STATIONS; COUNT SUCCESSFUL RESULTS, REPORT
  ;; One REST request per station, one at a time: about 900 requests, several
  ;; minutes. Do this only once, then read the saved .edn file.

  ;; Copied from pyregence.weather-stations.ca/fetch-rest-observation!
  ;; (private there), without the logging.
  (defn fetch-rest-observation!
    "Fetches the latest observation of a station from the REST API. Returns a
    map of :observation (nil when there's none) and :status, the API's response
    status (nil when the request didn't complete)."
    [msc-id]
    (let [url                   (str "https://api.weather.gc.ca"
                                     "/collections/swob-realtime/items?"
                                     (client/generate-query-string {:f            "json"
                                                                    :sortby       "-date_tm-value"
                                                                    :limit        1
                                                                    :msc_id-value msc-id}))
          {:keys [status body]} (try
                                  (client/get url {:connection-timeout 5000
                                                   :socket-timeout     10000
                                                   :throw-exceptions   false})
                                  (catch Exception _
                                    nil))]
      {:observation (when (= 200 status)
                      (ca/rest-response->observation (json/read-str body :key-fn keyword)))
       :status      status}))

  ;; msc_id -> {:observation :status}. Stations without an msc_id are skipped:
  ;; the query would return any station's observation. Pauses 250 ms before
  ;; each request, so they don't burst.
  (def core-observations
    (into {}
          (for [{{:keys [msc_id]} :properties} core-stations
                :when msc_id]
            (do (Thread/sleep 250)
                [msc_id (fetch-rest-observation! msc_id)]))))
  (spit (str output-dir "/swob-observations-" timestamp ".edn") core-observations)

  ;; Read them back instead of fetching again
  (def core-observations (edn/read-string (slurp (str output-dir "/swob-observations-" read-timestamp ".edn"))))

  ;; How many core stations the REST API has an observation for
  (def core-observations-report
    {:stations-queried (count core-observations)
     :with-observation (count (filter (fn [[_ result]] (:observation result)) core-observations))
     :statuses         (frequencies (map (fn [[_ result]] (:status result)) core-observations))})
    ;; => {:stations-queried 938,
    ;;     :with-observation 926,
    ;;     :statuses {200 934, nil 4}}


  ;; MAKE A MAP OF THE LISTINGS FOR CORE STATIONS
  ;; FIND XML FILES FOR THE CORE STATIONS; COUNT NUMBER OF OBSERVATIONS GAINED

  ;; FETCH ALL OBSERVATIONS FOR PARTNER STATIONS; COUNT SUCCESSFUL RESULTS, REPORT
  ;; Same as for the core stations: about 2000 requests, about 25 minutes.
  ;; Do this only once, then read the saved .edn file.

  ;; msc_id -> {:observation :status}
  (def partner-observations
    (into {}
          (for [{{:keys [msc_id]} :properties} partner-stations
                :when msc_id]
            (do (Thread/sleep 250)
                [msc_id (fetch-rest-observation! msc_id)]))))
  (spit (str output-dir "/swob-partner-observations-" timestamp ".edn") partner-observations)

  ;; Read them back instead of fetching again
  (def partner-observations (edn/read-string (slurp (str output-dir "/swob-partner-observations-" read-timestamp ".edn"))))

  ;; How many partner stations the REST API has an observation for
  (def partner-observations-report
    {:stations-queried (count partner-observations)
     :with-observation (count (filter (fn [[_ result]] (:observation result)) partner-observations))
     :statuses         (frequencies (map (fn [[_ result]] (:status result)) partner-observations))})

    ;; => {:stations-queried 2030,
    ;;     :with-observation 693,
    ;;     :statuses {200 2026, nil 4}}

  ;; MAKE A MAP OF THE LISTINGS FOR THE PARTNER STATIONS (network/date/...)
  ;; Partner XMLs live in <root>/partners/<network>/<YYYYMMDD>/<folder>/<files>,
  ;; where <root> is today/observations/swob-ml for the current UTC date, and
  ;; <YYYYMMDD>/WXO-DD/observations/swob-ml for past dates (kept ~30 days).
  ;; We list yesterday's folders: today's fill up during the day, yesterday's
  ;; are complete. One request for the networks, then one per network (~30).
  ;; Do this only once, then read the saved .edn file.

  ;; Copied from pyregence.weather-stations.ca.xml/fetch-dd! (private there)
  (defn fetch-dd!
    "GETs a path on the XML server. Returns {:status :body}, or {:error} when
    the request doesn't complete (timeouts included)."
    [path]
    (try
      (select-keys (client/get (str "https://dd.weather.gc.ca" path)
                               {:connection-timeout 5000
                                :socket-timeout     10000
                                :throw-exceptions   false})
                   [:status :body])
      (catch Exception e
        {:error (str (.getSimpleName (class e)) ": " (ex-message e))})))

  ;; Copied from pyregence.weather-stations.ca.xml/fetch-listing! (private there)
  (defn fetch-listing!
    "The listing at path (which must end in /, or the server answers with a
    redirect), making up to attempts requests when they don't complete.
    Returns {:listing} or {:status}/{:error} for a failed request. A listing
    is {:dirs [...] :files [...]}."
    [path attempts]
    (loop [attempt 1]
      (let [{:keys [status body error]} (fetch-dd! path)]
        (cond
          (= 200 status)                   {:listing (xml-ca/parse-listing body)}
          (and error (< attempt attempts)) (recur (inc attempt))
          error                            {:error error}
          :else                            {:status status}))))

  ;; Yesterday's UTC date, as YYYYMMDD
  (def listing-date (.format
                     (.minusDays (java.time.LocalDate/now java.time.ZoneOffset/UTC) 1)
                     (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd")))

  (def partners-path (str "/" listing-date "/WXO-DD/observations/swob-ml/partners/"))

  ;; The partner networks: the folders in partners/
  (def partner-networks
    (:dirs (:listing (fetch-listing! partners-path 2))))

  ;; network -> the station folders in its listing for listing-date. A network
  ;; whose listing failed maps to nil. Pauses 250 ms before each request.
  (def network->folders
    (into {}
          (for [network partner-networks]
            (do (Thread/sleep 250)
                [network (:dirs (:listing (fetch-listing! (str partners-path network "/" listing-date "/") 2)))]))))
  (spit (str output-dir "/swob-partner-folders-" timestamp ".edn") network->folders)

  ;; Read them back instead of fetching again
  (def network->folders (edn/read-string (slurp (str output-dir "/swob-partner-folders-" read-timestamp ".edn"))))

  ;; How many networks and folders there are
  (def network->folders-report
    {:networks            (count network->folders)
     :failed-listings     (vec (for [[network folders] network->folders
                                     :when (nil? folders)]
                                 network))
     :folders             (reduce + (map count (vals network->folders)))
     :folders-per-network (into (sorted-map)
                                (for [[network folders] network->folders]
                                  [network (count folders)]))})
    ;; => {:networks 30,
    ;;     :failed-listings ["ab-firewx"],
    ;;     :folders 1658,
    ;;     :folders-per-network
    ;;     {"ab-firewx" 0,
    ;;      "ab_agriculture" 166,
    ;;      "bc-RioTinto" 6,
    ;;      "bc-crd" 9,
    ;;      "bc-env-aq" 55,
    ;;      "bc-env-snow" 113,
    ;;      "bc-forestry" 119,
    ;;      "bc-hydro" 96,
    ;;      "bc-mvrd" 25,
    ;;      "bc-tran" 176,
    ;;      "dnd-ccg-lighthouse" 25,
    ;;      "mb_agriculture" 118,
    ;;      "nb-firewx" 27,
    ;;      "nb-rwin" 32,
    ;;      "nl-firewx" 22,
    ;;      "nl-water" 13,
    ;;      "ns-firewx" 30,
    ;;      "ns-rwin" 44,
    ;;      "nt-forestry" 38,
    ;;      "nt-water" 6,
    ;;      "on-firewx" 130,
    ;;      "on-grca" 19,
    ;;      "on-mto" 190,
    ;;      "on-trca" 28,
    ;;      "pc-firewx" 45,
    ;;      "pe-rwin" 5,
    ;;      "qc-pom" 5,
    ;;      "sk-forestry" 86,
    ;;      "yt-avalanche" 3,
    ;;      "yt-firewx" 27}}

  ;; A CSV of every partner station folder, to browse them on the server
  (defn write-partner-folders-csv!
    "Writes a CSV with one row per station folder in network->folders:
    network, folder, and the folder's URL. Each network's rows come after a
    row with only the network folder's URL. The URLs use listing-date, so they
    point at yesterday's folders. Returns the CSV's path."
    [network->folders]
    (let [csv-path (str output-dir "/swob-partner-folders-" timestamp ".csv")
          header   ["network" "folder" "url"]
          rows     (mapcat (fn [network]
                             (let [network-url (str "https://dd.weather.gc.ca" partners-path network "/" listing-date "/")
                                   divider     [network-url "" ""]
                                   folder-rows (for [folder (sort (get network->folders network))]
                                                 [network folder (str network-url folder "/")])]
                               (cons divider folder-rows)))
                           (sort (keys network->folders)))]
      (with-open [writer (io/writer csv-path)]
        (csv/write-csv writer (cons header rows)))
      csv-path))

  (write-partner-folders-csv! network->folders)


  ;; FIND XMLS FOR THE PARTNER STATIONS USING MULTIPLE PATTERNS
  ;; A station's XMLs are in its folder, so finding the folder in yesterday's
  ;; listings stands for finding its XMLs. No requests: this only uses
  ;; network->folders.

  ;; Every network a folder name appears in, eg {"ala" #{"ab_agriculture"}}
  (def folder->networks
    (let [folder-network-pairs (for [[network folders] network->folders
                                     folder            folders]
                                 [folder network])]
      (reduce (fn [acc [folder network]]
                (update acc folder (fnil conj #{}) network))
              {}
              folder-network-pairs)))

  ;; Adapted from pyregence.weather-stations.ca.xml/partner-folder-rules:
  ;; each rule names its match type, and which REST property it reads.
  (def partner-folder-rules
    [;; AB-MAF_ALA, iata_id ALA -> ala
     {:match-type    "IATA"
      :property      :iata_id
      :value->folder (fn [value] (str/lower-case value))}
     ;; DND lighthouses: iata_id TRIAL IS -> trial_is
     {:match-type    "IATA, spaces to _"
      :property      :iata_id
      :value->folder (fn [value] (str/replace (str/lower-case value) " " "_"))}
     ;; POM_quai_42 -> pom_quai_42, YAA_YAAFRA -> yaa_yaafra
     {:match-type    "msc_id"
      :property      :msc_id
      :value->folder (fn [value] (str/lower-case value))}])

  ;; Adapted from pyregence.weather-stations.ca.xml/match-partner-folder
  (defn match-partner-folder
    "The first rule whose folder exists in folder->networks, for a partner
    station's REST :properties. Returns {:match-type :matched-value :folder
    :networks}, or nil when no rule matches."
    [folder->networks properties]
    (some (fn [{:keys [match-type property value->folder]}]
            (let [value    (get properties property)
                  folder   (when value (value->folder value))
                  
                  ;; THIS IS THE KEY LINE IN THE WHOLE SCRIPT!!!!!
                  ;; This is the line that answers "Can we find an XML for this
                  ;; station in any of the networks we scanned earlier?"

                  ;; It's here that we try to find a folder with observations
                  ;; based on the properties we found in the REST API.

                  ;; This serves two purposes:
                  ;; - To check if a folder matching the REST API station exists
                  ;; - To find out if more than one network has a matching file
                  ;; This part here acknowledges that a given folder
                  ;; (msc_id, iata) can be found in more than one network
                  networks (get folder->networks folder)]
              (when networks
                {:match-type    match-type
                 :matched-value value
                 :folder        folder
                 :networks      networks})))
          partner-folder-rules))

  ;; msc_id -> match, for the partner stations some rule matches
  (def partner-matches
    (into {}
          (for [{:keys [properties]} partner-stations
                :let [msc-id (:msc_id properties)
                      match  (match-partner-folder folder->networks properties)]

                ;; Only add a match if the msc_id exists, and
                ;; a match was found
                :when (and msc-id match)]
            [msc-id match])))

  ;; COUNT SUCCESSFUL FINDS
  (def partner-matches-report
    {:partner-stations (count partner-stations)
     :matched          (count partner-matches)
     :unmatched        (- (count partner-stations) (count partner-matches))
     :by-match-type    (frequencies (map :match-type (vals partner-matches)))})
    ;; => {:partner-stations 2030,
    ;;     :matched 1638,
    ;;     :unmatched 392,
    ;;     :by-match-type {"IATA" 1623, "msc_id" 8, "IATA, spaces to _" 7}}

  ;; A CSV of every partner station folder, with the stations matched to it
  (defn build-folder-rows
    "The CSV rows of one folder: one per station matched to it, or one with
    empty match columns when no station is."
    [network network-url folder matches]
    (let [folder-url (str network-url folder "/")]
      (if (empty? matches)
        [[network folder folder-url "" "" ""]]
        (for [[msc-id {:keys [match-type matched-value]}] (sort-by first matches)]
          [network folder folder-url match-type matched-value msc-id]))))

  (defn write-partner-matches-csv!
    "Writes a CSV like write-partner-folders-csv!'s, plus the stations matched
    to each folder: match type, the REST value matched (iata_id or msc_id),
    and the station's msc_id. A folder with several stations gets one row per
    station. Stations without a folder don't appear. Returns the CSV's path."
    [network->folders partner-matches]
    (let [csv-path        (str output-dir "/swob-partner-matches-" timestamp ".csv")
          header          ["network" "folder" "url" "match_type" "matched_value" "msc_id"]
          folder->matches (group-by (fn [[_ match]] (:folder match)) partner-matches)
          rows            (mapcat (fn [network]
                                    (let [network-url (str "https://dd.weather.gc.ca" partners-path network "/" listing-date "/")
                                          divider     [network-url "" "" "" "" ""]
                                          folder-rows (mapcat (fn [folder]
                                                                (build-folder-rows network network-url folder (get folder->matches folder)))
                                                              (sort (get network->folders network)))]
                                      (cons divider folder-rows)))
                                  (sort (keys network->folders)))]
      (with-open [writer (io/writer csv-path)]
        (csv/write-csv writer (cons header rows)))
      csv-path))

  (write-partner-matches-csv! network->folders partner-matches)

  ;; IDENTIFY CASES IN WHICH MULTIPLE XMLs HAVE THE SAME IATA ID
  ;; A folder name found in several networks: a station matched to it could
  ;; be in any of them.

  ;; folder -> its networks, for the folders in more than one network
  (def folders-in-several-networks
    (into (sorted-map)
          (for [[folder networks] folder->networks
                :when (> (count networks) 1)]
            [folder networks])))

  (count folders-in-several-networks)
  (def num-networks-per-folder
    (mapv
                                (fn [[folder networks]] [folder (count networks)])
                                folders-in-several-networks)) 
  ;; At most a folder name appears in 3 different networks
  ;; It seems that we can afford to make the calls rather
  ;; than keep a memory of provider-to-network mapping. This
  ;; allows us to reduce the complexity a little bit.

  ;; This is the part that deals with Daniel's comment on
  ;; IATA ids potentially being found in multiple networks.
    ;; => [["138" 2]
    ;;     ["144" 2]
    ;;     ["146" 2]
    ;;     ["179" 2]
    ;;     ["182" 2]
    ;;     ["192" 2]
    ;;     ["206" 2]
    ;;     ["208" 2]
    ;;     ["209" 3]
    ;;     ["210" 2]
    ;;     ["212" 3]
    ;;     ["218" 2]
    ;;     ["222" 2]
    ;;     ["225" 2]
    ;;     ["230" 2]
    ;;     ["232" 2]
    ;;     ["235" 2]
    ;;     ["239" 2]
    ;;     ["267" 2]
    ;;     ["425" 2]
    ;;     ["503" 2]
    ;;     ["528" 2]
    ;;     ["542" 2]
    ;;     ["543" 2]
    ;;     ["546" 2]
    ;;     ["549" 2]
    ;;     ["550" 2]
    ;;     ["56" 2]
    ;;     ["563" 2]
    ;;     ["571" 2]
    ;;     ["599" 2]
    ;;     ["bar" 2]
    ;;     ["broc" 2]
    ;;     ["clea" 2]
    ;;     ["fll" 2]
    ;;     ["green" 2]
    ;;     ["msk" 2]
    ;;     ["nat" 2]]

  ;; FIND THE XML OF A STATION WHOSE FOLDER IS IN ONE NETWORK ONLY
  ;; Partner XMLs live in <root>/partners/<network>/<YYYYMMDD>/<folder>/<files>.
  ;; The station's newest XML is in today's folder, or in yesterday's just
  ;; after 00:00 UTC, before today's has any.

  ;; Copied from pyregence.weather-stations.ca.xml/get-utc-date (private there)
  (defn get-utc-date
    "The UTC date days-ago days before today, as YYYYMMDD."
    [days-ago]
    (.format (.minusDays (java.time.LocalDate/now java.time.ZoneOffset/UTC) days-ago)
             (java.time.format.DateTimeFormatter/ofPattern "yyyyMMdd")))

  ;; Adapted from pyregence.weather-stations.ca.xml/swob-root-path and
  ;; partner-folder-path
  (defn build-partner-folder-path
    "The folder holding a partner station's XML files for a date (YYYYMMDD).
    today/ only serves the current UTC date; past dates live under <date>/WXO-DD/."
    [date today network folder]
    (let [root (if (= date today)
                 "/today/observations/swob-ml/"
                 (str "/" date "/WXO-DD/observations/swob-ml/"))]
      (str root "partners/" network "/" date "/" folder "/")))

  ;; Copied from pyregence.weather-stations.ca.xml/pick-newest-xml
  (defn pick-newest-xml
    "The newest XML file in a station folder listing. File names start with the
    observation time in every naming scheme seen (2026-10-27-1800-ab-maf-ala-…,
    20261027T2340Z_DND-CCG_…), so the newest sorts last."
    [files]
    (->> files
         (filter #(str/ends-with? % ".xml"))
         sort
         last))

  ;; Adapted from pyregence.weather-stations.ca.xml/find-partner-xml-path!
  (defn find-partner-xml-path!
    "The path of the newest XML in a partner station's folder: today's, or
    yesterday's when today's is missing or has no XML yet. One listing request
    per date tried. Returns {:path} or {:status}."
    [network folder]
    (let [today     (get-utc-date 0)
          yesterday (get-utc-date 1)]
      (or (some (fn [date]
                  (let [folder-path (build-partner-folder-path date today network folder)
                        files       (:files (:listing (fetch-listing! folder-path 1)))
                        newest      (pick-newest-xml files)]
                    (when newest
                      {:path (str folder-path newest)})))
                [today yesterday])
          {:status "no XML in today's or yesterday's folder"})))

  ;; msc_id -> match, for the matched stations whose folder is in one network
  (def single-network-matches
    (into (sorted-map)
          (for [[msc-id match] partner-matches
                :when (= 1 (count (:networks match)))]
            [msc-id match])))

  (count single-network-matches)
  ;; => 1560

  ;; Try it on one of them
  (def example-msc-id
    (first (keys single-network-matches)))
    ;; => "1018238"

  (def example-xml-path
    (let [{:keys [networks folder]} (get single-network-matches example-msc-id)]
      (:path (find-partner-xml-path! (first networks) folder))))
    ;; => "/today/observations/swob-ml/partners/dnd-ccg-lighthouse/20261102/trial_is/20261102T2040Z_DND-CCG_SWOB_1018238.xml"

  ;; FETCH THE XML AND CONFIRM ITS MSC_ID
  ;; A SWOB XML holds one <element name=… uom=… value=…/> per value, msc_id
  ;; among them. The station's msc_id must be the XML's: otherwise the folder
  ;; is another station's.

  ;; Copied from pyregence.weather-stations.ca.xml/parse-swob-elements
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

  (defn fetch-xml-elements!
    "Fetches the XML at path and parses its elements. Returns {:elements}, or
    {:status}/{:error} when the request fails or the XML can't be parsed."
    [path]
    (let [{:keys [status body error]} (fetch-dd! path)]
      (cond
        error          {:error error}
        (= 200 status) (try
                         {:elements (parse-swob-elements body)}
                         (catch Exception e
                           {:error (str "unreadable: " (ex-message e))}))
        :else          {:status status})))

  (def example-elements (:elements (fetch-xml-elements! example-xml-path)))
  (def example-xml-msc-id (get-in example-elements ["msc_id" :value]))

  ;; true when the XML is the station's
  (= example-msc-id example-xml-msc-id)

  ;; ATTEMPT TO DEDUPLICATE BY MSC_ID USING FILE PATH ONLY
  ;; Partner XML file names contain the station's msc_id, lowercased, with _
  ;; turned into -, in every naming scheme sampled:
  ;;   AB-MAI_BROC   -> 2026-11-01-2300-ab-mai-broc-AUTO-swob.xml
  ;;   BC_ENV-AQ_138 -> 2026-11-01-2300-bc-env-aq-138-AUTO-swob.xml
  ;;   1063298       -> 20261101T2330Z_DND-CCG_SWOB_1063298.xml
  ;; while a same-name folder of another network holds another station's:
  ;;   bc-forestry/…/138/2026-11-01-2300-bc-wmb-138-AUTO-swob.xml
  ;; So for a folder name in several networks (3 at most), we look at the
  ;; newest XML of each network's folder, one at a time, until one's name has
  ;; the station's msc_id in it.

  (defn msc-id->file-name-part
    "The part of a partner XML file name that identifies the station, eg
    AB-MAI_BROC -> ab-mai-broc."
    [msc-id]
    (str/replace (str/lower-case msc-id) "_" "-"))

  (defn find-xml-path-by-file-name!
    "Tries the station's candidate networks one at a time, until the newest XML
    in the network's folder has the station's msc_id in its file name. Returns
    {:network :path}, or nil when none has. Pauses 250 ms before each network."
    [msc-id {:keys [folder networks]}]
    (let [file-name-part (msc-id->file-name-part msc-id)]
      (some (fn [network]
              (Thread/sleep 250)
              (let [{:keys [path]} (find-partner-xml-path! network folder)
                    file-name      (when path (last (str/split path #"/")))]
                (when (and file-name (str/includes? (str/lower-case file-name) file-name-part))
                  {:network network
                   :path    path})))
            (sort networks))))

  ;; msc_id -> match, for the matched stations whose folder is in several networks
  (def several-network-matches
    (into (sorted-map)
          (for [[msc-id match] partner-matches
                :when (> (count (:networks match)) 1)]
            [msc-id match])))

  (count several-network-matches)
  ;; => 78

  ;; A map of msc_id -> {:network :path}, or nil when no file name has the msc_id.
  ;; One or two listing requests per network tried.
  ;; Do this only once, then read the saved .edn file.
  (def several-network-results
    (into (sorted-map)
          (for [[msc-id match] several-network-matches]
            [msc-id (find-xml-path-by-file-name! msc-id match)])))
  (spit (str output-dir "/swob-partner-several-network-results-" timestamp ".edn") several-network-results)

  ;; Read them back instead of fetching again
  (def several-network-results (edn/read-string (slurp (str output-dir "/swob-partner-several-network-results-" read-timestamp ".edn"))))

  ;; COUNT THE NUMBER OF PARTNER XMLS DEDUPLICATED BY PATH ALONE
  (def several-network-report
    {:stations              (count several-network-results)
     :resolved-by-file-name (count (filter (fn [[_ result]] result) several-network-results))
     :unresolved            (vec (for [[msc-id result] several-network-results
                                       :when (nil? result)]
                                   msc-id))})
    ;; => {:stations 78, :resolved-by-file-name 78, :unresolved []}

  ;; COUNT THE NUMBER OF PARTNER XMLS WHOSE DEDUPLICATION REQUIRED READING THE XML FILE
  ;; --> zero, see numbers of the report above

  ;; REPORT ALL PARTNER XMLS FOUND
  ;; Every matched station goes through find-xml-path-by-file-name!: a folder
  ;; in one network is just the case with one candidate. This also confirms
  ;; the single-network matches, since the file name must have the station's
  ;; msc_id. About 1600 stations, one or two listing requests per network
  ;; tried: about 15–25 minutes.
  ;; Do this only once, then read the saved .edn file.

  ;; msc_id -> {:network :path}, or nil when no file name has the msc_id
  (def partner-xml-results
    (into (sorted-map)
          (for [[msc-id match] partner-matches]
            [msc-id (find-xml-path-by-file-name! msc-id match)])))
  (spit (str output-dir "/swob-partner-xml-results-" timestamp ".edn") partner-xml-results)

  ;; Read them back instead of fetching again
  (def partner-xml-results (edn/read-string (slurp (str output-dir "/swob-partner-xml-results-" read-timestamp ".edn"))))

  (defn build-partner-xml-row
    "What we know of a partner station's XML. match is its entry in
    partner-matches, and result its entry in partner-xml-results (both nil
    when the station has no folder). :status is \"found\", \"no file with
    msc_id\" or \"no folder\"."
    [msc-id match result]
    (cond
      (nil? match)
      {:msc-id          msc-id
       :network-count   0
       :network-success ""
       :folder          ""
       :xml-url         ""
       :status          "no folder"}

      (nil? result)
      {:msc-id          msc-id
       :network-count   (count (:networks match))
       :network-success ""
       :folder          (:folder match)
       :xml-url         ""
       :status          "no file with msc_id"}

      :else
      {:msc-id          msc-id
       :network-count   (count (:networks match))
       :network-success (:network result)
       :folder          (:folder match)
       :xml-url         (str "https://dd.weather.gc.ca" (:path result))
       :status          "found"}))

  ;; One row per partner station, by msc_id
  (def partner-xml-rows
    (for [msc-id (sort (for [{{:keys [msc_id]} :properties} partner-stations
                             :when msc_id]
                         msc_id))]
      (build-partner-xml-row msc-id
                             (get partner-matches msc-id)
                             (get partner-xml-results msc-id))))

  (def partner-xmls-report
    {:by-status              (frequencies (map :status partner-xml-rows))
     ;; network count -> stations found, eg {1 1560, 2 75, 3 3}
     :found-by-network-count (frequencies (map :network-count
                                               (filter (fn [row] (= "found" (:status row)))
                                                       partner-xml-rows)))})
    ;; => {:by-status
    ;;     {"found" 1416, "no folder" 392, "no file with msc_id" 222},
    ;;     :found-by-network-count {1 1338, 2 72, 3 6}}

  (defn write-partner-xmls-csv!
    "Writes a CSV with one row per partner station: msc_id, how many networks
    have its folder name, the network its XML was found in, its folder, the
    XML's URL, and the status. Returns the CSV's path."
    [partner-xml-rows]
    (let [csv-path (str output-dir "/swob-partner-xmls-" timestamp ".csv")
          header   ["msc_id" "network_count" "network_success" "folder" "xml_url" "status"]
          rows     (for [{:keys [msc-id network-count network-success folder xml-url status]} partner-xml-rows]
                     [msc-id (str network-count) network-success folder xml-url status])]
      (with-open [writer (io/writer csv-path)]
        (csv/write-csv writer (cons header rows)))
      csv-path))

  (write-partner-xmls-csv! partner-xml-rows)

  )
