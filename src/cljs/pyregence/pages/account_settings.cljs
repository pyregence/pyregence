(ns pyregence.pages.account-settings
  (:require
   [pyregence.api.directory               :as directory]
   [pyregence.components.mapbox           :as mb]
   [pyregence.components.nav-bar          :refer [nav-bar]]
   [pyregence.components.settings.nav-bar :refer [side-nav-bar-and-page]]
   [pyregence.datatypes.session           :as session]
   [pyregence.datatypes.viewer            :as viewer]
   [pyregence.state                       :as !]
   [pyregence.utils.browser-utils         :as u-browser]))

(defn- remeasure!
  "Note how much room the page has now, and let the map catch up."
  []
  (u-browser/scroll-to-top!)
  (reset! !/mobile? (u-browser/narrow-window?))
  (u-browser/after-the-layout-settles! mb/resize-map!))

(defn root-component
  [a-directory _]
  (u-browser/when-the-window-changes-shape! remeasure!)
  ;; Asked here and in near-term-forecast's initialize!, because each page loads
  ;; on its own and the nav bar needs the answer to decide whether to offer the
  ;; PSPS zones. Asked twice, not written twice: the routes, the role branch and
  ;; the refusal all live behind the directory now.
  ;;
  ;; Nothing waits on it and nothing copies out of it. The directory holds what
  ;; PyreCast said in reagent atoms, so reading it below is what makes this page
  ;; re-render when the answer arrives -- and what PyreCast last said stays said
  ;; until it says something else, which is the disappearance PYR1-1623 is about.
  (directory/refresh! a-directory)
  (fn [_ {:keys [user-role] :as m}]
    [:div
     {:style {:height         "100vh"
              :margin-bottom  "40px"
              :display        "flex"
              :flex-direction "column"
              :font-family    "Roboto"
              :padding-bottom "60px"}}
     [nav-bar {:logged-in?         true
               :mobile?            @!/mobile?
               :on-forecast-select #(u-browser/jump-to-url! (str "/?forecast=" (name %)))
               :a-viewer           (viewer/->viewer (session/->session user-role)
                                                    (directory/organizations   a-directory)
                                                    (directory/psps-backed-ids a-directory))}]
     [side-nav-bar-and-page m]]))
