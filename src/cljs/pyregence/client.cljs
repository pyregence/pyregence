(ns ^:figwheel-hooks pyregence.client
  (:require [pyregence.pages.home :as home]
            [pyregence.pages.not-found :as not-found]))

;; FIXME: Add more pages to this as they are created.
(def path->init
  {"/" home/init})

(defn ^:after-load mount-root! []
  (let [url-path (-> js/window .-location .-pathname)
        init-fn  (path->init url-path not-found/init)]
    (.log js/console (str "Running init function for " url-path))
    (init-fn)))
