(ns core-support.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-js include-css html5]]))


(defpartial layout [& content]
            (html5
              [:head
               [:title "Core Support Schedule"]
               (include-css "/css/bootstrap.css")
               (include-css "/css/bootstrap-responsive.css")
               [:style "body { padding-top: 60px; }"]]
              [:body
               (list
                [:div.navbar.navbar-fixed-top {"data-toggle" "collapse" "data-target" ".nav-collapse"}
                 [:div.navbar-inner
                  [:div.container
                   [:a.btn.btn-navbar
                    [:span.icon-bar]]
                  [:a.brand "Core Support Schedule"]
                   [:div.nav-collapse
                    [:ul.nav.navbar-nav.navbar-left
                     [:li.active
                      [:a {"href" "/"} "Home"]] 
                     [:li.active
                      [:a {"href" "/admin"} "Admin"]]]
                    [:ul.nav.navbar-nav.navbar-right
                     [:li.active
                      [:a {"href" "/login"} "Login"]]]
                     ]]]]
                [:div.container content] 
                (include-js "http://ajax.googleapis.com/ajax/libs/jquery/1.7.1/jquery.min.js")
                (include-js "/js/bootstrap.min.js"))]))
