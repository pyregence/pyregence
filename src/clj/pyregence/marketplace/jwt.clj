(ns pyregence.marketplace.jwt
  (:require [buddy.sign.jwt        :as jwt]
            [clj-http.client       :as http]
            [clojure.data.json     :as json]
            [clojure.string        :as str]
            [clojure.tools.logging :as log]
            [triangulum.config     :refer [get-config]])
  (:import [java.io            ByteArrayInputStream]
           [java.security.cert Certificate CertificateFactory]
           [java.util          Base64 Base64$Decoder]))

(def ^:private keys-url
  "https://www.googleapis.com/robot/v1/metadata/x509/cloud-commerce-partner@system.gserviceaccount.com")

(def ^:private issuer keys-url)

(def ^:private cache-ttl-ms (* 60 60 1000))

(def ^:private clock-skew-secs 30)

(defonce ^:private key-cache (atom {:fetched-at 0 :pubkeys nil}))

(def ^:private fetch-lock (Object.))

(defn- pem->pubkey [pem]
  (let [cf (CertificateFactory/getInstance "X.509")
        bs (String/.getBytes pem)]
    (-> (CertificateFactory/.generateCertificate cf (ByteArrayInputStream. bs))
        (Certificate/.getPublicKey))))

(defn- fetch-keys! []
  (log/info "Fetching Google Marketplace public keys")
  (try
    (let [response (http/get keys-url
                             {:as                 :json
                              :connection-timeout 5000
                              :socket-timeout     5000
                              :throw-exceptions   true})
          certs    (:body response)]
      (into {}
            (map (fn [[kid pem]] [(name kid) (pem->pubkey pem)]))
            certs))
    (catch Exception e
      (log/error e "Failed to fetch Google public keys")
      (throw (ex-info "Cannot fetch Google public keys" {:type :key-fetch-failed} e)))))

(defn- refresh-cache! []
  (locking fetch-lock
    (let [{:keys [fetched-at pubkeys]} @key-cache
          now (System/currentTimeMillis)]
      (if (and pubkeys (< (- now fetched-at) 5000))
        pubkeys
        (let [new-pubkeys (fetch-keys!)]
          (reset! key-cache {:fetched-at now :pubkeys new-pubkeys})
          new-pubkeys)))))

(defn- cached-keys! []
  (let [{:keys [fetched-at pubkeys]} @key-cache]
    (if (or (nil? pubkeys) (> (- (System/currentTimeMillis) fetched-at) cache-ttl-ms))
      (refresh-cache!)
      pubkeys)))

(defn- kid->pubkey [kid]
  (or (get (cached-keys!) kid)
      (do (log/warn "Key ID not found, refreshing cache" {:kid kid})
          (get (refresh-cache!) kid))))

(defn- token->header [token]
  (let [parts (str/split token #"\.")]
    (when (< (count parts) 3)
      (throw (ex-info "Malformed JWT: expected 3 parts" {:type :invalid-format})))
    (try
      (-> (Base64$Decoder/.decode (Base64/getUrlDecoder) (first parts))
          (String.)
          (json/read-str :key-fn keyword))
      (catch Exception e
        (throw (ex-info "Cannot decode JWT header" {:type :invalid-format} e))))))

(defn- validate-claims [claims]
  (let [audience (get-config :pyregence.marketplace/config :audience)
        {:keys [aud exp iat iss sub]} claims
        now      (quot (System/currentTimeMillis) 1000)
        errors   (cond-> []
                 (not= iss issuer)
                 (conj {:error :invalid-issuer :field :iss :expected issuer :got iss})

                 (not= aud audience)
                 (conj {:error :invalid-audience :field :aud :expected audience :got aud})

                 (str/blank? sub)
                 (conj {:error :empty-subject :field :sub})

                 (nil? exp)
                 (conj {:error :missing-expiration :field :exp})

                 (and exp (<= exp (- now clock-skew-secs)))
                 (conj {:error :token-expired :field :exp :exp exp :now now})

                 (nil? iat)
                 (conj {:error :missing-issued-at :field :iat})

                 (and iat (> iat (+ now clock-skew-secs)))
                 (conj {:error :issued-in-future :field :iat :iat iat :now now})

                 (and iat exp (> (- exp iat) 300))
                 (conj {:error :validity-too-long :field :exp}))]
    (when (seq errors)
      (log/warn "JWT claim validation failed" {:errors errors})
      (throw (ex-info "Invalid JWT claims" {:errors errors :type :invalid-claims})))
    claims))

(defn validate
  "Validates a Google Marketplace JWT. Returns claims map or throws ex-info."
  [token]
  (when (str/blank? token)
    (throw (ex-info "Empty JWT token" {:type :invalid-format})))

  (let [{:keys [kid alg]} (token->header token)]
    (when-not (= alg "RS256")
      (throw (ex-info "Unsupported algorithm" {:alg alg :type :invalid-format})))
    (let [pubkey (kid->pubkey kid)]
      (when-not pubkey
        (throw (ex-info "Unknown key ID" {:kid kid :type :invalid-signature})))
      (try
        (validate-claims (jwt/unsign token pubkey {:alg :rs256}))
        (catch clojure.lang.ExceptionInfo e
          (if (= :invalid-claims (:type (ex-data e)))
            (throw e)
            (throw (ex-info "JWT signature verification failed"
                            {:type :invalid-signature} e))))
        (catch Exception e
          (throw (ex-info "JWT verification failed"
                          {:type :invalid-signature} e)))))))

(defn claims->orders
  "Extracts [{:order-id X}] from JWT claims. Falls back to legacy single-order mode."
  [claims]
  (if-let [orders (not-empty (:orders claims))]
    (mapv (fn [o]
            {:order-id (if (string? o)
                         o
                         (or (:orderId o) (:order-id o) (str o)))})
          orders)
    (do (log/warn "JWT has no orders array - using legacy single-order mode")
        [{:order-id (str "legacy-" (:sub claims))}])))

(defn request->token
  "Extracts the x-gcp-marketplace-token from a Ring request, or nil."
  [request]
  (let [token (or (get-in request [:form-params "x-gcp-marketplace-token"])
                  (get-in request [:params :x-gcp-marketplace-token])
                  (get-in request [:params :token]))]
    (when-not (str/blank? token) token)))

^:rct/test
(comment
  (try (validate "") (catch Exception e (:type (ex-data e))))                    ;=> :invalid-format
  (try (validate "x.y") (catch Exception e (:type (ex-data e))))                 ;=> :invalid-format
  (:order-id (first (claims->orders {:orders ["ORD-1"] :sub "u1"})))             ;=> "ORD-1"
  (:order-id (first (claims->orders {:orders [{:orderId "ORD-2"}] :sub "u1"})))  ;=> "ORD-2"
  (:order-id (first (claims->orders {:sub "u1"})))                               ;=> "legacy-u1"
  (request->token {:params {:x-gcp-marketplace-token "tok"}})                    ;=> "tok"
  (pos? (count (cached-keys!)))                                                  ;=> true (requires network)
  )
