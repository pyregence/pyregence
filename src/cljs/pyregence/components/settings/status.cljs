(ns pyregence.components.settings.status)

(def statuses ["accepted" "pending" "none"])

(def status->display
  {"accepted" "Accepted"
   "pending" "Pending"
   ;; TODO consider renaming the data in the DB if were going to display it another way,
   ;; This kind of aliasing can lead to issues in communication.
   "none" "Archive / Inactive"})
