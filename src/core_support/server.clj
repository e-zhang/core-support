(ns core-support.server
  (:require [noir.server :as server]
            [core-support.models.team :as team]))

(team/init)

(server/load-views-ns 'core-support.views)

(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))]
    (server/start port {:mode mode
                        :ns 'core-support})))

