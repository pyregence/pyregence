(ns pyregece.routing
  (:require [pyregence.authentication :as authentication]
            [pyregence.cameras        :as cameras]
            [pyregence.capabilities   :as capabilities]
            [pyregence.email          :as email]
            [pyregence.match-drop     :as match-drop]
            [pyregence.red-flag       :as red-flag]
            [triangulum.views         :refer [render-page]]))

(def routes
  {;; Page Routes
   [:get "/"]                   {:handler     (render-page "/")}
   [:get "/admin"]              {:handler     (render-page "/admin")
                                 :auth-type   :admin
                                 :auth-action :redirect}
   [:get "/dashboard"]          {:handler     (render-page "/dashboard")
                                 :auth-type   :match-drop
                                 :auth-action :redirect}
   [:get "/help"]               {:handler     (render-page "/help")}
   [:get "/login"]              {:handler     (render-page "/login")}
   [:get "/forecast"]           {:handler     (render-page "/forecast")}
   [:get "/long-term-forecast"] {:handler     (render-page "/long-term-forecast")}
   [:get "/near-term-forecast"] {:handler     (render-page "/near-term-forecast")}
   [:get "/privacy-policy"]     {:handler     (render-page "/privacy-policy")}
   [:get "/register"]           {:handler     (render-page "/register")}
   [:get "/reset-password"]     {:handler     (render-page "/reset-password")}
   [:get "/terms-of-use"]       {:handler     (render-page "/terms-of-use")}
   [:get "/verify-email"]       {:handler     (render-page "/verify-email")}

   ;; Users API
   [:post "/add-new-user"]                  {:handler     authentication/add-new-user}
   [:post "/add-org-user"]                  {:handler     authentication/add-org-user
                                             :auth-type   :admin
                                             :auth-action :block}
   [:get  "/get-email-by-user-id"]          {:handler     authentication/get-email-by-user-id}
   [:get  "/get-organizations"]             {:handler     authentication/get-organizations}
   [:get  "/get-org-non-member-users"]      {:handler     authentication/get-org-non-member-users}
   [:get  "/get-org-member-users"]          {:handler     authentication/get-org-member-users}
   [:get  "/get-psps-organizations"]        {:handler     authentication/get-psps-organizations}
   [:get  "/get-user-info"]                 {:handler     authentication/get-user-info
                                             :auth-type   :user
                                             :auth-action :block}
   [:get  "/get-user-match-drop-access"]    {:handler     authentication/get-user-match-drop-access
                                             :auth-type   :user
                                             :auth-action :block}
   [:post "/log-in"]                        {:handler     authentication/log-in}
   [:post "/log-out"]                       {:handler     authentication/log-out}
   [:post "/remove-org-user"]               {:handler     authentication/remove-org-user
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/set-user-password"]             {:handler     authentication/set-user-password}
   [:get  "/user-email-taken"]              {:handler     authentication/user-email-taken}
   [:post "/update-org-info"]               {:handler     authentication/update-org-info
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/update-org-user-role"]          {:handler     authentication/update-org-user-role
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/update-user-info"]              {:handler     authentication/update-user-info
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/update-user-match-drop-access"] {:handler     authentication/update-user-match-drop-access
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/update-user-name"]              {:handler     authentication/update-user-name
                                             :auth-type   :admin
                                             :auth-action :block}
   [:post "/verify-user-email"]             {:handler     authentication/verify-user-email}

   ;; Cameras API
   [:get "/get-cameras"]       {:handler cameras/get-cameras}
   [:get "/get-current-image"] {:handler cameras/get-current-image}

   ;; Capabilities API
   [:get "/get-all-layers"]       {:handler     capabilities/get-all-layers}
   [:get "/get-fire-names"]       {:handler     capabilities/get-fire-names}
   [:get "/get-layers"]           {:handler     capabilities/get-layers}
   [:get "/get-layer-name"]       {:handler     capabilities/get-layer-name}
   [:get "/get-user-layers"]      {:handler     capabilities/get-user-layers
                                   :auth-type   :user
                                   :auth-action :block}
   [:get "/set-capabilities"]     {:handler     capabilities/set-capabilities!}
   [:get "/set-all-capabilities"] {:handler     capabilities/set-all-capabilities!}
   [:get "/remove-workspace"]     {:handler     capabilities/remove-workspace!}

   ;; Email API
   [:get "/send-email"] {:handler email/send-email!}

   ;; Match Drop API
   [:post "/delete-match-drop"]      {:handler     match-drop/delete-match-drop!
                                      :auth-type   :match-drop
                                      :auth-action :block}
   [:get  "/get-md-available-dates"] {:handler     match-drop/get-md-available-dates
                                      :auth-type   :match-drop
                                      :auth-action :block}
   [:get  "/get-md-status"]          {:handler     match-drop/get-md-status
                                      :auth-type   :match-drop
                                      :auth-action :block}
   [:get  "/get-match-drops"]        {:handler     match-drop/get-match-drops
                                      :auth-type   :match-drop
                                      :auth-action :block}
   [:get  "/initiate-md"]            {:handler     match-drop/initiate-md!
                                      :auth-type   :match-drop
                                      :auth-action :block}

   ;; Red Flag API
   [:get "/get-red-flag-layer"] {:handler red-flag/get-red-flag-layer}})
