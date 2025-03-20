(ns pyregence.routing
  (:require [pyregence.authentication :as authentication]
            [pyregence.cameras        :as cameras]
            [pyregence.capabilities   :as capabilities]
            [pyregence.email          :as email]
            [pyregence.handlers       :refer [clj-handler]]
            [pyregence.match-drop     :as match-drop]
            [pyregence.red-flag       :as red-flag]
            [triangulum.views         :refer [render-page]]))

(def routes
  {;; Page Routes
   [:get "/"]                                   {:handler     (render-page "/")}
   [:get "/admin"]                              {:handler     (render-page "/admin")
                                                 :auth-type   :admin
                                                 :auth-action :redirect}
   [:get "/dashboard"]                          {:handler     (render-page "/dashboard")
                                                 :auth-type   :user
                                                 :auth-action :redirect}
   [:get "/help"]                               {:handler     (render-page "/help")}
   [:get "/login"]                              {:handler     (render-page "/login")}
   [:get "/forecast"]                           {:handler     (render-page "/forecast")}
   [:get "/long-term-forecast"]                 {:handler     (render-page "/long-term-forecast")}
   [:get "/near-term-forecast"]                 {:handler     (render-page "/near-term-forecast")}
   [:get "/privacy-policy"]                     {:handler     (render-page "/privacy-policy")}
   [:get "/register"]                           {:handler     (render-page "/register")}
   [:get "/reset-password"]                     {:handler     (render-page "/reset-password")}
   [:get "/terms-of-use"]                       {:handler     (render-page "/terms-of-use")}
   [:get "/verify-email"]                       {:handler     (render-page "/verify-email")}

   ;; Users API
   [:post "/clj/add-new-user"]                  {:handler     (clj-handler authentication/add-new-user)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/add-org-user"]                  {:handler     (clj-handler authentication/add-org-user)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/get-email-by-user-id"]          {:handler     (clj-handler authentication/get-email-by-user-id)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-organizations"]             {:handler     (clj-handler authentication/get-organizations)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-org-non-member-users"]      {:handler     (clj-handler authentication/get-org-non-member-users)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-org-member-users"]          {:handler     (clj-handler authentication/get-org-member-users)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-psps-organizations"]        {:handler     (clj-handler authentication/get-psps-organizations)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-user-info"]                 {:handler     (clj-handler authentication/get-user-info)
                                                 :auth-type   #{:user :token}
                                                 :auth-action :block}
   [:post "/clj/get-user-match-drop-access"]    {:handler     (clj-handler authentication/get-user-match-drop-access)
                                                 :auth-type   #{:user :token}
                                                 :auth-action :block}
   [:post "/clj/log-in"]                        {:handler     (clj-handler authentication/log-in)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/log-out"]                       {:handler     (clj-handler authentication/log-out)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/remove-org-user"]               {:handler     (clj-handler authentication/remove-org-user)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/set-user-password"]             {:handler     (clj-handler authentication/set-user-password)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/user-email-taken"]              {:handler     (clj-handler authentication/user-email-taken)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/update-org-info"]               {:handler     (clj-handler authentication/update-org-info)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/update-org-user-role"]          {:handler     (clj-handler authentication/update-org-user-role)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/update-user-info"]              {:handler     (clj-handler authentication/update-user-info)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/update-user-match-drop-access"] {:handler     (clj-handler authentication/update-user-match-drop-access)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/update-user-name"]              {:handler     (clj-handler authentication/update-user-name)
                                                 :auth-type   #{:admin :token}
                                                 :auth-action :block}
   [:post "/clj/verify-user-email"]             {:handler     (clj-handler authentication/verify-user-email)
                                                 :auth-type   :token
                                                 :auth-action :block}

   ;; Cameras API
   [:post "/clj/get-cameras"]                   {:handler     (clj-handler cameras/get-cameras)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-current-image"]             {:handler     (clj-handler cameras/get-current-image)
                                                 :auth-type   :token
                                                 :auth-action :block}

   ;; Capabilities API
   [:post "/clj/get-all-layers"]                {:handler     (clj-handler capabilities/get-all-layers)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-fire-names"]                {:handler     (clj-handler capabilities/get-fire-names)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-layers"]                    {:handler     (clj-handler capabilities/get-layers)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-layer-name"]                {:handler     (clj-handler capabilities/get-layer-name)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:post "/clj/get-user-layers"]               {:handler     (clj-handler capabilities/get-user-layers)
                                                 :auth-type   #{:user :token}
                                                 :auth-action :block}
   [:get  "/clj/set-capabilities"]              {:handler     (clj-handler capabilities/set-capabilities!)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:get  "/clj/set-all-capabilities"]          {:handler     (clj-handler capabilities/set-all-capabilities!)
                                                 :auth-type   :token
                                                 :auth-action :block}
   [:get  "/clj/remove-workspace"]              {:handler     (clj-handler capabilities/remove-workspace!)
                                                 :auth-type   :token
                                                 :auth-action :block}

   ;; Email API
   [:post "/clj/send-email"]                    {:handler     (clj-handler email/send-email!)
                                                 :auth-type   :token
                                                 :auth-action :block}

   ;; Match Drop API
   [:post "/clj/delete-match-drop"]             {:handler     (clj-handler match-drop/delete-match-drop!)
                                                 :auth-type   #{:match-drop :token}
                                                 :auth-action :block}
   [:post "/clj/get-md-available-dates"]        {:handler     (clj-handler match-drop/get-md-available-dates)
                                                 :auth-type   #{:match-drop :token}
                                                 :auth-action :block}
   [:post "/clj/get-md-status"]                 {:handler     (clj-handler match-drop/get-md-status)
                                                 :auth-type   #{:match-drop :token}
                                                 :auth-action :block}
   [:post "/clj/get-match-drops"]               {:handler     (clj-handler match-drop/get-match-drops)
                                                 :auth-type   #{:match-drop :token}
                                                 :auth-action :block}
   [:post "/clj/initiate-md"]                   {:handler     (clj-handler match-drop/initiate-md!)
                                                 :auth-type   #{:match-drop :token}
                                                 :auth-action :block}

   ;; Red Flag API
   [:post "/clj/get-red-flag-layer"]            {:handler     (clj-handler red-flag/get-red-flag-layer)
                                                 :auth-type   :token
                                                 :auth-action :block}})
