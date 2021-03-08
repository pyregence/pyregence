(ns pyregence.components.svg-icons)

(defn layers []
  [:svg {:enable-background "new 0 0 96 96"
         :viewBox "0 0 96 96"}
   [:polygon {:points "87,61.516 48,81.016 9,61.516 0,66.016 48,90.016 96,66.016 "}]
   [:polygon {:points "87,44.531 48,64.031 9,44.531 0,49.031 48,73.031 96,49.031 "}]
   [:path {:d "M48,16.943L78.111,32L48,47.057L17.889,32L48,16.943 M48,8L0,32l48,24l48-24L48,8L48,8z"}]])

(defn center-on-point []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M0 0h48v48h-48z"
           :fill "none"}]
   [:path {:d "M24 16c-4.42 0-8 3.58-8 8s3.58 8 8 8 8-3.58 8-8-3.58-8-8-8zm17.88 6c-.92-8.34-7.54-14.96-15.88-15.88v-4.12h-4v4.12c-8.34.92-14.96 7.54-15.88 15.88h-4.12v4h4.12c.92 8.34 7.54 14.96 15.88 15.88v4.12h4v-4.12c8.34-.92 14.96-7.54 15.88-15.88h4.12v-4h-4.12zm-17.88 16c-7.73 0-14-6.27-14-14s6.27-14 14-14 14 6.27 14 14-6.27 14-14 14z"}]])

(defn extent []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M0 0h48v48h-48z"
           :fill "none"}]
   [:path {:d "M6 10v8h4v-8h8v-4h-8c-2.21 0-4 1.79-4 4zm4 20h-4v8c0 2.21 1.79 4 4 4h8v-4h-8v-8zm28 8h-8v4h8c2.21 0 4-1.79 4-4v-8h-4v8zm0-32h-8v4h8v8h4v-8c0-2.21-1.79-4-4-4z"}]])

(defn info []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M0 0h48v48h-48z"
           :fill "none"}]
   [:path {:d "M22 34h4v-12h-4v12zm2-30c-11.05 0-20 8.95-20 20s8.95 20 20 20 20-8.95 20-20-8.95-20-20-20zm0 36c-8.82 0-16-7.18-16-16s7.18-16 16-16 16 7.18 16 16-7.18 16-16 16zm-2-22h4v-4h-4v4z"}]])

(defn legend []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M8 21c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3zm0-12c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3zm0 24.33c-1.47 0-2.67 1.19-2.67 2.67s1.2 2.67 2.67 2.67 2.67-1.19 2.67-2.67-1.2-2.67-2.67-2.67zm6 4.67h28v-4h-28v4zm0-12h28v-4h-28v4zm0-16v4h28v-4h-28z"}]
   [:path {:d "M0 0h48v48h-48z"
           :fill "none"}]])

(defn measure []
  [:svg {:viewBox "0 0 64 64"}
   [:path {:d "M45.44,5.84,5.84,45.44,18.56,58.16l39.6-39.6ZM18.56,55.33,8.67,45.44l2.82-2.83L15,46.14l1.41-1.41-3.53-3.54,2.83-2.83,5,5L22.1,41.9l-5-4.95L20,34.12l3.53,3.54,1.42-1.42-3.54-3.53,2.83-2.83,5,4.95,1.42-1.42-4.95-4.95,2.82-2.82L32,29.17l1.41-1.41-3.53-3.54,2.83-2.83,4.95,4.95,1.41-1.41-5-4.95L37,17.15l3.54,3.54,1.41-1.42-3.54-3.53,2.83-2.83,5,4.95,1.42-1.42-5-5,2.83-2.82,9.89,9.89Z"}]])

(defn my-location []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M0-.17h48v48h-48z"
           :fill "none"}]
   [:path {:d "M41.88 22.17c-.92-8.34-7.54-14.96-15.88-15.88v-4.12h-4v4.12c-8.34.92-14.96 7.54-15.88 15.88h-4.12v4h4.12c.92 8.34 7.54 14.96 15.88 15.88v4.12h4v-4.12c8.34-.92 14.96-7.54 15.88-15.88h4.12v-4h-4.12zm-17.88 16c-7.73 0-14-6.27-14-14s6.27-14 14-14 14 6.27 14 14-6.27 14-14 14z"}]])

(defn next-button []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M12 36l17-12-17-12v24zm20-24v24h4V12h-4z"}]
   [:path {:d "M0 0h48v48H0z"
           :fill "none"}]])

(defn pause-button []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M12 38h8V10h-8v28zm16-28v28h8V10h-8z"}]
   [:path {:d "M0 0h48v48H0z"
           :fill "none"}]])

(defn pin []
  [:svg {:viewBox "0 0 32 32"}
   [:path {:d "M4 12 A12 12 0 0 1 28 12 C28 20, 16 32, 16 32 C16 32, 4 20 4 12 M11 12 A5 5 0 0 0 21 12 A5 5 0 0 0 11 12 Z"}]])

(defn play-button []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M-838-2232H562v3600H-838z"
           :fill "none"}]
   [:path {:d "M16 10v28l22-14z"}]
   [:path {:d "M0 0h48v48H0z"
           :fill "none"}]])

(defn previous-button []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M12 12h4v24h-4zm7 12l17 12V12z"}]
   [:path {:d "M0 0h48v48H0z"
           :fill "none"}]])

(defn zoom-in []
  [:svg {:viewBox "0 0 512 512"
         :xml-space "preserve"}
   [:polygon {:points "448,224 288,224 288,64 224,64 224,224 64,224 64,288 224,288 224,448 288,448 288,288 448,288 "}]])

(defn zoom-out []
  [:svg {:viewBox "0 0 512 512"
         :xml-space "preserve"}
   [:rect {:height "64" :width "384" :x "64" :y "224"}]])

(defn close []
  [:svg {:viewBox "0 0 48 48"}
   [:path {:d "M38 12.83l-2.83-2.83-11.17 11.17-11.17-11.17-2.83 2.83 11.17 11.17-11.17 11.17 2.83 2.83 11.17-11.17 11.17 11.17 2.83-2.83-11.17-11.17z"}]
   [:path {:d "M0 0h48v48h-48z" :fill "none"}]])

(defn help []
  [:svg {:viewBox "0 0 16 16"}
   [:g {:fill "none" :fill-rule "evenodd" :id "Icons with numbers" :stroke "none" :stroke-width "1"}
    [:g {:style {:fill "currentColor"} :id "Group" :transform "translate(-48.000000, -432.000000)"}
     [:path {:id "Oval 318" :d "M54.8796844,443.0591 L54.8796844,445 L57.2307692,445 L57.2307692,443.0591 Z M56,448 C51.5817218,448 48,444.418278 48,440 C48,435.581722 51.5817218,432 56,432 C60.4182782,432 64,435.581722 64,440 C64,444.418278 60.4182782,448 56,448 Z M53.5700197,435.51041 C52.5864514,436.043208 52.0631167,436.947609 52,438.22364 L54.2800789,438.22364 C54.2800789,437.852024 54.4076253,437.493845 54.6627219,437.149093 C54.9178185,436.804341 55.3504243,436.631968 55.9605523,436.631968 C56.5811997,436.631968 57.0085458,436.771881 57.2426036,437.051713 C57.4766613,437.331544 57.5936884,437.641592 57.5936884,437.981867 C57.5936884,438.277369 57.4884955,438.548241 57.2781065,438.794493 L56.8205128,439.190732 L56.2445759,439.573539 C55.6765258,439.949633 55.3241295,440.282067 55.1873767,440.570853 C55.0506239,440.859639 54.9664696,441.382356 54.9349112,442.139019 L57.0650888,442.139019 C57.0703485,441.780835 57.1045362,441.516679 57.1676529,441.346541 C57.2675876,441.077903 57.4700839,440.842849 57.7751479,440.64137 L58.3353057,440.271995 C58.9033559,439.895901 59.28731,439.586972 59.4871795,439.345198 C59.8290615,438.946718 60,438.456461 60,437.874412 C60,436.925225 59.6068415,436.208867 58.8205128,435.725319 C58.0341841,435.241771 57.0466858,435 55.8579882,435 C54.9533157,435 54.1906671,435.170135 53.5700197,435.51041 Z M53.5700197,435.51041"}]]]])

(defn flame []
  [:svg {:enable-background "new 0 0 96 96"
         :viewBox "0 0 855.492 855.492"}
   [:path {:d "M270.436,853.938c8.801,5.399,19.101-4.301,14-13.301c-13.399-23.8-21-51.199-21-80.5c0-62,34.301-111.6,84.801-144.6
              c44.699-29.2,32.5-66.9,39.899-111.8c4.4-26.601,21-50.8,34.9-66.9c5.6-6.5,16.1-3.399,17.5,5c8.2,50.4,73.6,136.2,111.3,192.2
              c43.4,64.6,40.6,121.5,40.3,125.8c0,0.2,0,0.4,0,0.601c-0.1,29.1-7.7,56.399-21,80c-5.1,9,5.2,18.8,14,13.3
              c69.4-42.9,119.4-113.7,136.101-193.601c13.3-63.6,5.8-129.3-15.7-190.1c-12.7-35.9-30.2-70-51.5-101.6
              c-68.9-102.5-188.8-259.601-203.2-351.5c-2.7-16.9-24.1-22.9-35.2-9.9c-0.2,0.2-20.6,22.7-33.399,47.6
              c-10.9,21.3-19.801,43.6-24.9,66.9c-9.6,43.9-7.9,90.9,3.1,134.4c3.601,14.2,8.2,28.1,13.801,41.6
              c5.399,13,11.199,26.1,12.199,40.3c1.601,26.5-22.399,49.4-48.399,49.4c-23.3,0-42.601-14-47.601-40.4
              c-1.3-6.8-8.899-10.399-14.899-6.899c-88.5,52.1-147.9,148.399-147.9,258.5C127.836,706.438,184.836,801.137,270.436,853.938z"}]])
