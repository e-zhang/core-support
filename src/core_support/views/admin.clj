(ns core-support.views.admin
  (:use noir.core
         hiccup.core
         hiccup.element
         hiccup.form
         hiccup.page)
  (:require [core-support.models.team :as team]
            [core-support.views.common :as common]
            [noir.validation :as valid]
            [noir.response :as resp]
            [clojure.string :as string]
            [noir.session :as session]))

(defpartial error-text [errors]
            [:span [:font {:color "red"} (string/join "" errors)]])


(pre-route "/admin*" {}
           (when-not (team/admin?)
             (resp/redirect "/login")))

(defpage "/login" {:as user}
          (if (team/admin?)
            (resp/redirect "/admin")
            (common/layout 
                [:h3 "Login for Admin Rights"]
                [:ul.actions
                  [:li (link-to {:class "submit"} "/create" "New")]]
                (form-to [:post "/login"]
                         [:ul.actions 
                            (text-field {:placeholder "username"} :username (user :username))
                            (password-field {:placeholder "password"} :password)
                            (submit-button {:class "submit"} "submit")])
                            (valid/on-error :username error-text))))
                
  
(defpage [:post "/login"] {:as user}
    (if (team/login! user)
        (resp/redirect "/admin")
        (render "/login" user)))

(defn create-fields []
  (form-to [:post "/create"]
           [:form {:role "form" :class "form-horizontal"}
            [:div {:class "form-group"}
              (label {:class "control-label"} "name" "Name") 
              (text-field {:placeholder "name"} "name")]
            [:div {:class "form-group"}
              (label {:class "control-label"} "project" "Project" )
              (drop-down "project" [["Execution" "execution"]
                                   ["MarketData" "marketdata"]
                                   ["Helix" "helix"]])]
            (valid/on-error :create error-text)
            (submit-button {:class "submit"} "submit")]))

(defpage "/create" []
  (common/layout
    [:h3 "Create new entry for team member"]
    (create-fields)))

                

(defpage [:post "/create"] {:as user}
  (team/create! user)
  (render "/create"))
    


(defpage "/admin" []
    (common/layout 
        [:h3 "Core Support Admin Page"]     
        [:ul.items (team/get-all-members)]))
