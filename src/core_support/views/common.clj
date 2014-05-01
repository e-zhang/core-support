(ns core-support.views.common
  (:use [noir.core :only [defpartial]]
	[noir.session :as session]
        [core-support.models.team :as team]
        [hiccup.element :only [link-to]]
        [hiccup.page :only [include-js include-css html5]]))


(defn create-support-schedule [create]
  [:div {:class "row"}
    (->> (team/get-weekdays) 
         (map team/get-support-schedule)
         (map create))])

(defpartial layout [& content]
            (html5
              [:head
               [:title "Core Support Schedule"]
               (include-css "/bootstrap-3.1.1-dist/css/bootstrap.css")
               (include-css "/bootstrap-3.1.1-dist/css/bootstrap-theme.css") 
	       (include-css "/css/today.css")]
              [:body
               (list
                [:div.navbar.navbar-default {:role "navigation"}
                 [:div.navbar-header  {:data-toggle "collapse" :data-target "#navbar-collapse"}
                   [:btn.navbar-toggle
                    [:span.icon-bar]]
                   [:a.navbar-brand "Core Support Schedule"]]
                   [:div.navbar-collapse {:id"navbar-collapse" }
                    [:ul.nav.navbar-nav.navbar-left
                     [:li
                      [:a {"href" "/"} "Home"]] 
                     [:li
                      [:a {"href" "/admin"} "Admin"]]]
                    [:ul.nav.navbar-nav.navbar-right
                     [:li
		      (if (session/get :admin)
			[:p {:class "navbar-text"} (session/get :username)]
                        [:a {"href" "/login"} "Login"])]]
                     ]]
                [:div.container content] 
                (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.11.0/jquery.min.js")
                (include-js "http://ajax.googleapis.com/ajax/libs/jqueryui/1.10.4/jquery-ui.min.js")
		(include-js "/js/admin.jquery.js")
                (include-js "/bootstrap-3.1.1-dist/js/bootstrap.js"))]))


(defpartial admin-layout [& content]
    (layout
        [:h3 "Core Support Admin Page"]     
	[:div [:ul.nav.nav-pills
	  [:li (link-to "/admin" "Actions")]
	  [:li (link-to "/admin/team" "Team")]
	  [:li (link-to "/admin/schedule" "Schedule")]]]
	[:br] 
	content))
