(ns pyregence.styles
  (:require-macros [herb.core :refer [defglobal]]
                   pyregence.herb-patch)
  (:require herb.runtime
            [clojure.string :as str]))

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
  [".ol-scale-line" {:padding ".4rem" :left "auto" :right "8px" :background-color "rgba(0, 0, 0, .7)"}]
  [".ol-scale-line-inner" {:font-size ".75rem"}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helper Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn combine [& styles]
  (apply merge
         (for [style styles]
           (cond
             (map? style)    style
             (fn? style)     (style)
             (vector? style) (apply (first style) (rest style))))))

;; FIXME: Remove :sig-* colors since Pyregence is a CEC-EPIC product.
(defn color-picker
  ([color]
   (color-picker color 1))
  ([color alpha]
   (case color
     :blue            (str "rgba(0, 0, 175, "     alpha ")")
     :black           (str "rgba(0, 0, 0, "       alpha ")")
     :box-tan         (str "rgba(249, 248, 242, " alpha ")")
     :dark-green      (str "rgba(0, 100, 0, "     alpha ")")
     :light-gray      (str "rgba(230, 230, 230, " alpha ")")
     :header-tan      (str "rgba(237, 234, 219, " alpha ")")
     :red             (str "rgba(200, 0, 0, "     alpha ")")
     :sig-brown       (str "rgba(96, 64, 26, "    alpha ")")
     :sig-orange      (str "rgba(252, 178, 61, "  alpha ")")
     :sig-light-green (str "rgba(202, 217, 70, "  alpha ")")
     :sig-tan         (str "rgba(177, 162, 91, "  alpha ")")
     :sig-green       (str "rgba(106, 148, 71, "  alpha ")")
     :white           (str "rgba(255, 255, 255, " alpha ")")
     color)))

(defn to-merge? [modifiers key return]
  (when (contains? (set modifiers) key) return))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Pseudo / Class Functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn p-bordered-input [& modifiers]
  (let [base-style     {:background-color "white"
                        :border           "1px solid"
                        :border-color     (color-picker :sig-brown)
                        :border-radius    "2px"
                        :height           "1.75rem"
                        :padding          ".25rem .5rem"}
        flex-style     {:width            "100%"}
        multi-style    {:font-family      "inherit"
                        :height           "inherit"
                        :padding          ".3rem .5rem"
                        :resize           "vertical"}
        login-style    {:border-radius    "1.5px"
                        :border-color     (color-picker :header-tan)
                        :height           "2rem"}
        disabled-style {:background-color "rgb(236, 236, 236)"
                        :color            "black"}]
    (with-meta
      (merge base-style
             (to-merge? modifiers :flex-style  flex-style)
             (to-merge? modifiers :multi-style multi-style)
             (to-merge? modifiers :login-style login-style))
      {:key    (str/join "-" (sort modifiers))
       :group  true
       :pseudo {:disabled disabled-style}})))

(defn p-button [& modifiers]
  (let [base-style     {:border-width  "0"
                        :border-radius "3px"
                        :color         "white"
                        :cursor        "pointer"
                        :font-size     ".9rem"
                        :font-weight   "normal"
                        :min-width     "10rem"
                        :text-align    "center"
                        :padding       ".5rem .75rem"}
        disabled-style {:cursor        "not-allowed"
                        :opacity       "0.5"}]
    (with-meta
      (if (contains? (set modifiers) :disabled)
        (merge base-style disabled-style)
        base-style)
      {:key    (str/join "-" (sort modifiers))
       :group  true
       :pseudo {:disabled disabled-style}})))

(defn p-dark-link []
  (with-meta
    {:color           (color-picker :sig-green)
     :cursor          "pointer"
     :text-decoration "none"}
    {:pseudo {:hover {:color (color-picker :sig-light-green)}}}))

(defn p-selectable [selected?]
  (with-meta
    {:background-color (if selected?
                         (color-picker :sig-light-green 0.5)
                         "white")}
    {:pseudo (if selected?
               {}
               {:hover {:background-color (color-picker :sig-light-green 0.1)}})
     :key    selected?
     :group  true}))

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

(defn modal []
  {:position         "fixed"
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
