(ns pyregence.components.login-menu
  (:require [clojure.core.async                 :refer [<! go]]
            [pyregence.components.common        :refer [tool-tip-wrapper]]
            [pyregence.components.svg-icons     :as svg]
            [pyregence.state                    :as !]
            [pyregence.styles                   :as $]
            [pyregence.utils.async-utils        :as u-async]
            [pyregence.utils.browser-utils      :as u-browser]))

(defn login-menu
  "A login and logout navigation menu item"
  [{:keys [user-id]}]
  [:div {:style {:position "absolute" :right "3rem" :top ".5rem"}}
   (when-not @!/mobile?
     (if user-id
       [:div {:style {:align-items "center" :display "flex"}}
        (when (> (count @!/user-org-list) 0)
          [tool-tip-wrapper
           "Visit the admin page"
           :top
           [:a {:style      ($/combine ($/fixed-size "1.5rem")
                                       {:cursor "pointer" :margin-right "1rem"})
                :aria-label "Visit the admin page"
                :href       "/admin"}
            [svg/admin-user]]])
        [:label {:style    {:cursor "pointer" :margin ".16rem .2rem 0 0" }
                 :on-click (fn []
                             (go (<! (u-async/call-clj-async! "log-out"))
                                 (-> js/window .-location .reload)))}
         "Log Out"]]
       ;; [:label {:style {:margin-right "1rem" :cursor "pointer"}
       ;;          :on-click #(u-browser/jump-to-url! "/register")} "Register"]
       [:label {:style    {:cursor "pointer" :margin ".16rem .2rem 0 0" }
                :on-click #(u-browser/jump-to-url! "/login")} "Log In"]))])
