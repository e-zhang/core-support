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
            [clojure.pprint :as pp]
            [monger.json]
            [cheshire.core :as json]
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
                (form-to {:class "form-inline" :role "form"} [:post "/login"]
			 [:div {:class "form-group"}
			    (label {:class "sr-only"} "username-label" "username")
                            (text-field {:placeholder "username" :class "form-control"} :username (user :username))]
			 [:div {:class "form-group"}
			    (label {:class "sr-only"} "password-label" "password")
                            (password-field {:placeholder "password" :class "form-control"} :password)]
		         (submit-button {:class "btn btn-default submit"} "submit")
                         (valid/on-error :username error-text)))))
                
  
(defpage [:post "/login"] {:as user}
    (if (team/login! user)
        (resp/redirect "/admin")
        (render "/login" user)))

(defn create-fields []
  (form-to {:role "form" :class "form-horizontal"}
	   [:post "/create"]
            [:div {:class "form-group"}
              (label {:class "control-label col-sm-2"} "name" "Name") 
              [:div {:class "col-sm-4"} (text-field {:placeholder "name" :class "form-control"} "name")]]
            [:div {:class "form-group"}
              (label {:class "control-label col-sm-2"} "project" "Project" )
              [:div {:class "col-sm-4"} 
		(drop-down {:class "form-control"} "project" 
				  [["Execution" "execution"]
                                   ["MarketData" "marketdata"]
                                   ["Helix" "helix"]])]]
	    [:div {:class "form-group"}
              [:p {:class "col-sm-1" } (valid/on-error :create error-text)]
              [:div {:class "col-sm-1"} (submit-button {:class "btn btn-default submit form-control"} "submit")]]))

(defpage "/create" []
  (common/layout
    [:h3 "Create new entry for team member"]
    (create-fields)))

                

(defpage [:post "/create"] {:as user}
  (team/create! user)
  (render "/create"))
    

(defpage [:post "/admin"] {:as action}
  (cond 
    (contains? action :reset) (team/reset-docs!)
    (contains? action :recalc) (team/get-weekly-schedule!) )
  (render "/admin"))

(defpage "/admin/team" []
    (common/admin-layout
        [:div (string/replace (json/generate-string (team/get-all-members) {:pretty true}) "\n" "<br>")] 
	[:br]))


(defpage [:post "/admin/schedule"] {:as swap}
	(pp/pprint swap)
	(team/swap-schedule! swap)
	(render "/admin/schedule"))

(defn create-support-dropdown [selected]
	(drop-down "newpartner"
		(map #(conj [] (:name %) (:name %)) (team/get-all-members))
		selected))


(defn create-support-item [schedule]
  (if schedule 
    [:div 
     (if (team/today? (:day schedule))
	{:class "col-md-2" :id "today"} 
	{:class "col-md-2"})
     [:h3 {:id "day"} (:day schedule)]
     [:br]
     [:div {:class "partner"} 
	[:span (:primary schedule)]
	(create-support-dropdown (:primary schedule))]
     [:div {:class "partner"} 
	[:span (:secondary schedule)]
	(create-support-dropdown (:secondary schedule))]]))

(defpage "/admin/schedule" []
   (common/admin-layout
       (common/create-support-schedule create-support-item)
	[:br]
	[:p (valid/on-error :swap error-text)]))
	

(defpage "/admin" []
    (common/admin-layout 
	[:div 
	  (link-to {:class "btn btn-default" :role "button"} "/create" "New")]
	[:div (form-to [:post "/admin"] 
                 [:p (valid/on-error :reset error-text)]
		 (hidden-field :reset)			
                 (submit-button {:class "btn btn-default submit"} "Reset Docs"))]
        [:div (form-to [:post "/admin"] 
                 [:p (valid/on-error :recalc error-text)]
		 (hidden-field :recalc)			
                 (submit-button {:class "btn btn-default submit"} "Recalc"))]))
