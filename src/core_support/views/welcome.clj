(ns core-support.views.welcome
  (:require [core-support.views.common :as common]
            [core-support.models.team :as team]
            [clj-time.core :as ct]
            [clj-time.periodic :as cp])
  (:use [noir.core :only [defpage]]))


(defn create-support-item [schedule]
  (if schedule 
    [:div 
     (if (team/today? (:day schedule))
	{:class "col-md-2" :id "today"} 
	{:class "col-md-2"})
     [:h3 (:day schedule)]
     [:br]
     [:div (:primary schedule)]
     [:div (:secondary schedule)]]))

(defpage "/" []
         (common/layout
           [:p "Welcome to core-support"]
           (common/create-support-schedule create-support-item)))
