(ns core-support.views.welcome
  (:require [core-support.views.common :as common]
            [core-support.models.team :as team]
            [clj-time.core :as ct]
            [clj-time.periodic :as cp])
  (:use [noir.core :only [defpage]]))


(defn get-support-item [schedule]
  (if schedule 
    [:div {:class "col-md-4"} 
     [:h3 (:day schedule) [:p (:partner1 schedule) "  -  " (:partner2 schedule)]]]))

(defn get-support-schedule []
  (team/get-weekly-schedule)
  [:div {:class "row"}
    (->> (team/get-weekdays) 
         (map team/get-support-schedule)
         (map get-support-item))])

(defpage "/" []
         (common/layout
           [:p "Welcome to core-support"]
           (get-support-schedule)))
