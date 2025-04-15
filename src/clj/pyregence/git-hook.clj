#!/usr/bin/env bb

;; NOTE this file must be self-contained - it will be executed as a standalone script.

(ns clj.pyregence.git-hook
  "A custom git-credential executable friendly to our Gitlab-Kubernetes combination,
  which resolves Gitlab Project Tokens from an EDN-encoded file."
  (:require [clojure.edn          :as edn]
            [clojure.java.io      :as io]
            [clojure.string       :as str]
            [babashka.http-client :as http]
            [cheshire.core        :as json]
            [babashka.fs          :as fs]
            [babashka.cli         :as cli]
            [clojure.pprint       :as pp]))

(def gitlab-token (System/getenv "GITLAB_TOKEN"))
(def gitlab-user  (System/getenv "USER"))
(def gitlab-url   "https://gitlab.sig-gis.com/api/v4")
(def headers      {"PRIVATE-TOKEN" gitlab-token
                   "CONTENT-TYPE" "application/json"
                   "ACCEPT" "application/json"})

(defn get-expiry []
  (-> (java.time.ZonedDateTime/now)
      (.plusDays 364)
      (.withZoneSameInstant (java.time.ZoneId/of "America/Los_Angeles"))
      (.format (java.time.format.DateTimeFormatter/ofPattern "Y-MM-d"))))

(defn get-gitlab [path]
  (-> (str gitlab-url path)
      (http/get {:headers headers})
      (get :body)
      (json/parse-string)))

(defn gen-token [[repo url]]
  (let [token-name      (str gitlab-user "_sig3_generated_" repo)
        token-spec      {:name         token-name
                         :scopes       ["read_api" "read_repository"]
                         :expires_at   (get-expiry)
                         :access_level 20}
        path            (clojure.string/replace (str repo) "/" "%2F")
        {:strs [token]} (-> (str gitlab-url "/projects/" path "/access_tokens")
                            (http/post {:body (json/encode token-spec) :headers headers})
                            (get :body)
                            (json/parse-string))]
    {url {"username" token-name "password" token}}))

(defn get-sig-deps
  [{:keys [deps]}]
  (into {}
        (comp (filter #(cond-> (second %) :git/url (str/includes? "gitlab.sig-gis.com")))
              (map (fn [[repo spec]] [repo (:git/url spec)])))
        deps))

(defn show-help
  [spec]
  (cli/format-opts (merge spec {:order (vec (keys (:spec spec)))})))

(defn -main
  [args]
  (try
    (let [opts (cli/parse-opts args cli-spec)]
      (if (or (:help opts) (:h opts))
        (println (show-help cli-spec))
        (let [sig-deps (-> (slurp (:deps opts))
                           (edn/read-string)
                           (get-sig-deps))]
          (spit (:out opts) (with-out-str (pp/pprint (into {} (map gen-token) sig-deps)))))))
    (catch Exception _
      (println (show-help cli-spec)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
