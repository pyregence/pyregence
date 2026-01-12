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

(defonce ^:private key-cache (atom {:fetched-at 0 :keys nil}))

(defn- pem->pubkey [pem]
  (let [cf (CertificateFactory/getInstance "X.509")
        bs (String/.getBytes pem)]
    (Certificate/.getPublicKey (CertificateFactory/.generateCertificate cf (ByteArrayInputStream. bs)))))

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
            (map (fn [[kid pem]]
                   [(name kid) (pem->pubkey pem)])
                 certs)))
    (catch Exception e
      (log/error e "Failed to fetch Google public keys")
      (throw (ex-info "Cannot fetch Google public keys" {:cause e :type :key-fetch-failed})))))

(defn- cached-keys! []
  (let [{:keys [fetched-at keys]} @key-cache
        now      (System/currentTimeMillis)
        expired? (> (- now fetched-at) cache-ttl-ms)]
    (if (or (nil? keys) expired?)
      (let [new-keys (fetch-keys!)]
        (reset! key-cache {:fetched-at now :keys new-keys})
        new-keys)
      keys)))

(defn- kid->pubkey [kid]
  (let [keys (cached-keys!)]
    (or (get keys kid)
        (let [_         (log/warn "Key ID not found, refreshing cache" {:kid kid})
              refreshed (fetch-keys!)]
          (reset! key-cache {:fetched-at (System/currentTimeMillis) :keys refreshed})
          (get refreshed kid)))))

(defn- token->header [token]
  (let [parts (str/split token #"\.")]
    (when (< (count parts) 3)
      (throw (ex-info "Malformed JWT: expected 3 parts" {:type :invalid-format})))
    (try
      (let [b64     (first parts)
            decoder (Base64/getUrlDecoder)]
        (json/read-str (String. (Base64$Decoder/.decode decoder b64)) :key-fn keyword))
      (catch Exception e
        (throw (ex-info "Cannot decode JWT header" {:cause (Throwable/.getMessage e) :type :invalid-format}))))))

(defn- validate-claims [claims]
  (let [audience (get-config :pyregence.marketplace/config :audience)
        {:keys [aud exp iat iss sub]} claims
        now      (quot (System/currentTimeMillis) 1000)
        errors   (cond-> []
                 (not= iss issuer)
                 (conj {:expected issuer :field :iss :got iss})

                 (not= aud audience)
                 (conj {:expected audience :field :aud :got aud})

                 (str/blank? sub)
                 (conj {:error "Subject is empty" :field :sub})

                 (nil? exp)
                 (conj {:error "Expiration missing" :field :exp})

                 (and exp (<= exp (- now clock-skew-secs)))
                 (conj {:error "Token expired" :exp exp :field :exp :now now})

                 (nil? iat)
                 (conj {:error "Issued-at missing" :field :iat})

                 (and iat (> iat (+ now clock-skew-secs)))
                 (conj {:error "Token issued in the future" :field :iat :iat iat :now now})

                 (and iat exp (> (- exp iat) 300))
                 (conj {:error "Token validity exceeds 5 minutes" :field :exp}))]
    (when (seq errors)
      (log/warn "JWT claim validation failed" {:errors errors})
      (throw (ex-info "Invalid JWT claims" {:errors errors :type :invalid-claims})))
    claims))

(defn- check-ntp-drift! []
  (try
    (let [remote-ts (-> (http/get "http://worldtimeapi.org/api/timezone/Etc/UTC"
                                  {:as :json :socket-timeout 2000})
                        :body :unixtime)
          drift     (Math/abs (- remote-ts (quot (System/currentTimeMillis) 1000)))]
      (cond
        (> drift 30) (log/error "NTP drift >30s, JWT validation may fail" {:drift drift})
        (> drift 10) (log/warn "NTP drift >10s" {:drift drift})
        :else        (log/info "NTP sync OK" {:drift drift})))
    (catch Exception _ (log/warn "NTP check failed"))))

(defonce ^:private ntp-checked (delay (check-ntp-drift!)))

(defn validate [token]
  @ntp-checked
  (when (str/blank? token)
    (throw (ex-info "Empty JWT token" {:type :invalid-format})))

  (let [header (token->header token)
        kid    (:kid header)
        alg    (:alg header)]
    (when (not= alg "RS256")
      (throw (ex-info "Unsupported algorithm" {:alg alg :type :invalid-format})))
    (let [pubkey (kid->pubkey kid)]
      (when-not pubkey
        (throw (ex-info "Unknown key ID" {:kid kid :type :invalid-signature})))
      (try
        (let [claims (jwt/unsign token pubkey {:alg :rs256})]
          (validate-claims claims))
        (catch clojure.lang.ExceptionInfo e
          (if (#{:invalid-claims} (:type (ex-data e)))
            (throw e)
            (throw (ex-info "JWT signature verification failed"
                            {:cause (Throwable/.getMessage e) :type :invalid-signature}))))
        (catch Exception e
          (throw (ex-info "JWT verification failed"
                          {:cause (Throwable/.getMessage e) :type :invalid-signature})))))))

(defn ->orders [claims]
  (let [orders (get claims :orders [])]
    (when (empty? orders)
      (log/warn "JWT has no orders array - using legacy single-order mode"))
    (if (seq orders)
      (mapv (fn [o]
              (if (string? o)
                {:order-id o :product-id "pyrecast"}
                {:order-id   (or (:orderId o) (:order-id o) (str o))
                 :product-id (or (:productId o) (:product-id o) "pyrecast")}))
            orders)
      [{:order-id   (str "legacy-" (:sub claims))
        :product-id "pyrecast"}])))

(defn request->token [request]
  (or (get-in request [:form-params "x-gcp-marketplace-token"])
      (get-in request [:params :x-gcp-marketplace-token])
      (get-in request [:params :token])
      (get-in request [:body :x-gcp-marketplace-token])))

^:rct/test
(comment
  (try (validate "") (catch Exception e (:type (ex-data e))))                      ;=> :invalid-format
  (try (validate "x.y") (catch Exception e (:type (ex-data e))))                   ;=> :invalid-format
  (:order-id (first (->orders {:orders ["ORD-1"] :sub "u1"})))                     ;=> "ORD-1"
  (:order-id (first (->orders {:orders [{:orderId "ORD-2"}] :sub "u1"})))          ;=> "ORD-2"
  (:order-id (first (->orders {:sub "u1"})))                                       ;=> "legacy-u1"
  (request->token {:params {:x-gcp-marketplace-token "tok"}})                      ;=> "tok"
  (pos? (count (cached-keys!)))                                                    ;=> true
  )
