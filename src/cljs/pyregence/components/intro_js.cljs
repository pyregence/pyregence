(ns pyregence.components.intro-js)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IntroJs Aliases
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def ^:private intro-js js/introJs)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tour Creation
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- tour-steps
  "Sets the steps and configuration options for the tour."
  []
  (clj->js {:steps               [{:title "Welcome to Pyrecast!"
                                   :intro "Hello world!"}
                                  {:intro "Lorem ipsum dolor sit amet, consectetur adipiscing elit."}
                                  {:element  (.querySelector js/document "#collapsible-panel")
                                   :intro    "Here is the Layer Selection panel."
                                   :position "right"}
                                  {:element  (.querySelector js/document "#zoom-bar")
                                   :intro    "Here is the zoom bar."
                                   :position "left"}
                                  {:element  (.querySelector js/document "#time-slider")
                                   :intro    "Here is the time slider."
                                   :position "left"}
                                  {:element  (.querySelector js/document "#tool-bar")
                                   :intro    "Here is the tool bar."
                                   :position "left"}
                                  {:element  (.querySelector js/document "#theme-select")
                                   :intro    "The theme selector."
                                   :position "bottom"}
                                  {:intro "Happy hacking!"}]
            :showProgress        false
            :showBullets         true
            :disabledInteraction true
            :exitOnEsc           true
            :exitOnOverlayClick  false
            :showStepNumbers     false}))

(defn init-tour!
  "Initializes the IntroJs tour."
  []
  (doto (intro-js)
        (.setOptions (tour-steps))
        (.start)))
