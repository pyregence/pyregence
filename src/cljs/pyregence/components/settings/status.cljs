(ns pyregence.components.settings.status
  (:require
   [clojure.string :as str]))

(defn status->display
  [status]
  (->>
   (str/split status #"_")
   (map str/capitalize)
   (str/join " ")))
