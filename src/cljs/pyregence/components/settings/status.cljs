(ns pyregence.components.settings.status
  (:require
   [clojure.string :as str]))

(def statuses
  ["accepted" "pending" "none"])

(defn status->display
  [status]
  (->>
   (str/split status #"_")
   (map str/capitalize)
   (str/join " ")))
