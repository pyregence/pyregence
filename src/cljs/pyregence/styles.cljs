(ns pyregence.styles
  (:require-macros [herb.core :refer [defglobal]]
                   pyregence.herb-patch)
  (:require herb.runtime
            [reagent.core :as r]
            [clojure.string :as str]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defonce light? (r/atom false))

(defn combine [& styles]
  (apply merge
         (for [style styles]
           (cond
             (map? style)    style
             (fn? style)     (style)
             (vector? style) (apply (first style) (rest style))))))

(defn color-picker
  ([color]
   (color-picker color 1))
  ([color alpha]
   (case color
     :bg-color     (if @light? "white" (str "rgba(50, 50, 50, .9)"))
     :border-color (if @light?
                     (str "rgba(0, 0, 0, "       alpha ")")
                     (str "rgba(255, 255, 255, " alpha ")"))
     :font-color   (if @light?
                     (color-picker :brown)
                     "rgb(235, 235, 235)")
     :yellow       (str "rgba(249, 175, 59, "  alpha ")")
     :blue         (str "rgba(0, 0, 175, "     alpha ")")
     :black        (str "rgba(0, 0, 0, "       alpha ")")
     :brown        (str "rgba(96, 65, 31, "    alpha ")")
     :box-tan      (str "rgba(249, 248, 242, " alpha ")")
     :dark-green   (str "rgba(0, 100, 0, "     alpha ")")
     :light-gray   (str "rgba(230, 230, 230, " alpha ")")
     :red          (str "rgba(200, 0, 0, "     alpha ")")
     :white        (str "rgba(255, 255, 255, " alpha ")")
     color)))

(defn to-merge? [modifiers key return]
  (when (contains? (set modifiers) key) return))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Global Styles
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defglobal general
  [:* {:box-sizing "border-box"
       :padding    "0px"
       :margin     "0px"}]

  [:label {:font-size     "1rem"
           :font-weight   "bold"
           :margin-bottom "0"}])

(defglobal body-and-content-divs
  [:body {:background-color "white"
          :position         "relative"
          :height           "100vh"}]
  [:#app {:height "100%"}]
  [:#near-term-forecast {:display        "flex"
                         :flex-direction "column"
                         :height         "100%"}]
  [:#exception {:padding "10px"}])

(defglobal over-write-styles
  [:#header {:position "static"}]
  [:#nav-row {:padding "0"}]
  [".ol-scale-line" {:background-color (color-picker :bg-color)
                     :border           (str "1px solid " (color-picker :border-color))
                     :bottom           "36px"
                     :box-shadow       (str "0 0 0 2px " (color-picker :bg-color))
                     :left             "auto"
                     :height           "28px"
                     :right            "64px"}]
  [".ol-scale-line-inner" {:border-color (color-picker :border-color)
                           :color        (color-picker :border-color)
                           :font-size    ".75rem"}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pseudo / Class Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn p-bordered-input [& modifiers]
  (let [base-style     {:background-color "white"
                        :border           "1px solid"
                        :border-color     (color-picker :sig-brown)
                        :border-radius    "2px"
                        :font-family      "inherit"
                        :height           "1.75rem"
                        :padding          ".25rem .5rem"}
        flex-style     {:width            "100%"}
        multi-style    {:height           "inherit"
                        :padding          ".3rem .5rem"
                        :resize           "vertical"}
        disabled-style {:background-color "rgb(236, 236, 236)"
                        :color            "black"}]
    (with-meta
      (merge base-style
             (to-merge? modifiers :flex-style  flex-style)
             (to-merge? modifiers :multi-style multi-style))
      {:key    (str/join "-" (sort modifiers))
       :group  true
       :pseudo {:disabled disabled-style}})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Style Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn color [color]
  {:color (color-picker color)})

(defn bg-color [color]
  {:background (color-picker color)})

(defn margin [& modifiers]
  (if (= 1 (count modifiers))
    {:margin (first modifiers)}
    (apply merge
           {}
           (for [[size position] (partition 2 modifiers)]
             (case position
               :t {:margin-top    size}
               :b {:margin-bottom size}
               :l {:margin-left   size}
               :r {:margin-right  size}
               :v {:margin-top    size
                   :margin-bottom size}
               :h {:margin-left   size
                   :margin-right  size}
               {})))))

(defn padding [& modifiers]
  (if (= 1 (count modifiers))
    {:padding (first modifiers)}
    (apply merge
           {}
           (for [[size position] (partition 2 modifiers)]
             (case position
               :t {:padding-top    size}
               :b {:padding-bottom size}
               :l {:padding-left   size}
               :r {:padding-right  size}
               :v {:padding-top    size
                   :padding-bottom size}
               :h {:padding-left   size
                   :padding-right  size}
               {})))))

(defn border [& modifiers]
  (if (= 1 (count modifiers))
    {:border (first modifiers)}
    (apply merge
           {}
           (for [[style position] (partition 2 modifiers)]
             (case position
               :t {:border-top    style}
               :b {:border-bottom style}
               :l {:border-left   style}
               :r {:border-right  style}
               :v {:border-top    style
                   :border-bottom style}
               :h {:border-left   style
                   :border-right  style}
               {})))))

(defn align [type position]
  (let [style (str (name type) "-" (name position))]
    (case (keyword style)
      :text-center  {:text-align "center"}

      :text-left    {:text-align "left"}

      :text-right   {:text-align "right"}

      :block-center {:display      "block"
                     :margin-left  "auto"
                     :margin-right "auto"}

      :block-left   {:display      "block"
                     :margin-left  "0"
                     :margin-right "auto"}

      :block-right  {:display      "block"
                     :margin-left  "auto"
                     :margin-right "0"}

      :flex-center  {:display         "flex"
                     :justify-content "center"
                     :align-items     "center"}

      :flex-left    {:display         "flex"
                     :justify-content "flex-start"}

      :flex-right   {:display         "flex"
                     :justify-content "flex-end"}

      :flex-top     {:display         "flex"
                     :align-items     "flex-start"}

      :flex-bottom  {:display         "flex"
                     :align-items     "flex-end"}
      {})))

(defn flex-row []
  {:align-items     "center"
   :display         "flex"
   :flex-direction  "row"
   :flex-wrap       "nowrap"
   :justify-content "space-between"})

(defn flex-col []
  {:align-items     "center"
   :display         "flex"
   :flex-direction  "column"
   :justify-content "flex-start"})

(defn font [& modifiers]
  (apply merge
         {}
         (for [[attr val] (partition 2 modifiers)]
           (when (contains? #{:style :variant :weight :size :family} attr)
             {(keyword (str "font-" (name attr))) val}))))

(defn disabled-group [disabled?]
  (if disabled?
    {:opacity        "0.4"
     :pointer-events "none"}
    {}))

(defn root []
  {:display        "flex"
   :flex-direction "column"
   :padding        "0 1rem"
   :align-items    "center"
   :flex           "1 1 0"
   :overflow       "auto"})

(defn modal [position]
  {:position         position
   :z-index          "1000"
   :left             "0"
   :top              "0"
   :width            "100%"
   :height           "100%"
   :background-color "rgba(0,0,0,0.4)"})

(defn outline [color]
  (let [rgb (color-picker color)]
    {:background   "white"
     :border-color rgb
     :border-style "solid"
     :color        rgb}))

(defn text-ellipsis []
  {:white-space   "nowrap"
   :overflow      "hidden"
   :text-overflow "ellipsis"})

(defn fixed-size [size]
  {:float      "left"
   :height     size
   :min-height size
   :min-width  size
   :width      size})

(defn tool []
  {:background-color (color-picker :bg-color)
   :border           (str "1px solid " (color-picker :border-color))
   :border-radius    "5px"
   :box-shadow       (str "0 0 0 2px " (color-picker :bg-color))
   :color            (color-picker :font-color)
   :position         "absolute"
   :z-index          "100"})

(defn p-button-hover []
  (with-meta
    {}
    {:pseudo {:hover {:background-color (color-picker :border-color 0.2)
                      :border-radius    "4px"}}}))

(def light-border (str "1.2px solid " (color-picker :brown)))

(defn action-box []
  {:background-color (color-picker :white)
   :border           light-border
   :border-radius    "5px"
   :overflow         "hidden"
   :display          "flex"
   :flex-direction   "column"
   :height           "100%"
   :padding          "0"
   :width            "100%"})

(defn action-header []
  {:background-color (color-picker :yellow)
   :border-bottom    light-border
   :align-items      "center"
   :display          "flex"
   :flex-direction   "row"
   :flex-wrap        "nowrap"
   :height           "2.5rem"
   :justify-content  "space-between"
   :padding          ".25rem .7rem"
   :width            "100%"})
