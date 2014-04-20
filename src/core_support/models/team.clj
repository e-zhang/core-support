(ns core-support.models.team
    (:require [monger.core :as mg]
              [monger.collection :as mc]
              [monger.result :as mr]
              [monger.operators :refer :all]
              [clj-time.core :as ct]
              [clj-time.coerce :as cc]
              [clj-time.periodic :as cp]
              [clj-time.format :as cf]
              [clojure.math.combinatorics :as comb]
              [noir.util.crypt :as crypt]
              [noir.validation :as valid]
              [noir.session :as session])
    (:import [com.mongodb MongoOptions ServerAddress]))
 
(def team-collection "team")
(def schedule-collection "schedule")
(def team-db "core-support")


(defn init []
    (let [uri (get (System/getenv) "MONGOHQ_URL" "mongodb://localhost:27017")]  
      (mg/connect!)
      (mg/set-db! (mg/get-db team-db))))
  

(defn admin? [] 
    (session/get :admin))

(defn login! [{:keys [username password] :as user}]
  (let [{stored-pass :password :or {:password nil}} 
        (mc/find-one-as-map team-collection {:name username})] 
    (if (and stored-pass (crypt/compare password stored-pass))
        (do (session/put! :admin true)
            (session/put! :username username))
        (valid/set-error :username "Invalid username or password"))))

(defn get-all-members []
    (mc/find-maps team-collection))


(defn get-support-schedule [date]
  (mc/find-one-as-map schedule-collection {:day (cf/unparse (cf/formatter "yyyy-MM-dd") date)}))

(defn init-member [member]
  (assoc member :password (crypt/encrypt "Spot123") 
                :lastpartner nil 
                :lastdate (cc/to-long (ct/epoch))))

(defn create! [member]
  (if (mc/find-one-as-map team-collection {:name (member :name)} ["name"]) 
    (valid/set-error :create (str (:name member) " already exists"))
    (if (mr/ok? (mc/insert team-collection (init-member member)))
      (valid/set-error :create (str (:name member) " succesfully added"))
      (valid/set-error :create (str (:name member) " could not be added")))))


(defn update-support! [date m [p1 p2 :as pair]]
  (let [datestring (cf/unparse (cf/formatter "yyyy-MM-dd") date)]
    (if-not (and (mr/ok? (mc/update team-collection {:name p1} {$set {:lastpartner p2 :lastdate (cc/to-long date)}}))
                 (mr/ok? (mc/update team-collection {:name p2} {$set {:lastpartner p1 :lastdate (cc/to-long date)}}))
                 (mr/ok? (mc/update schedule-collection {:day datestring} {$set {:partner1 p1 :partner2 p2}} :upsert true)))
      (println (format "Error updating support for day %s with pair %s, %s" (cons datestring pair)))
      (-> (assoc-in m [p1 :lastpartner] p2)
          (assoc-in [p1 :lastdate] (cc/to-long date))
          (assoc-in [p2 :lastpartner] p1)
          (assoc-in [p2 :lastdate] (cc/to-long date))))))


(defn get-time-score [to dates]
  (->> (seq dates) 
       (map #(min (cc/to-long to) %))
       (map cc/from-long)
       (map #(ct/in-days (ct/interval % to)))
       (map #(* % 5) %)
       (reduce +)))

(defn get-partner-score [[p1, p2 :as partners]]
  (if (some nil? (map :lastpartner partners))
    5
    (->> (seq [[(p1 :name) (p2 :lastpartner)] [(p2 :name) (p1 :lastpartner)]])
         (map #(= (first %) (last %)))
         (map #(if % -1 1))
         (reduce +)))) 

(defn score [date members]
  (if (apply = (map :project members))
    -1
    (+ (if (some #(= % "execution") (map :project members)) 10 0)
       (get-time-score date (map :lastdate members))
       (get-partner-score members)))) 

(defn get-best-score [members date]
  (->> (comb/combinations (keys members) 2)
       (map #(assoc {} :pair % :score (score date (vals (select-keys members %)))))
       (sort-by :score >)
       ));(first)))

(defn get-weekdays []
  (->> (cp/periodic-seq (ct/now) (ct/days 1)) 
       (drop-while #(> (ct/day-of-week %) 5))
       (take-while #(< (ct/day-of-week %) 6))))

(defn get-weekly-schedule []
  (let [m (get-all-members)
        members (zipmap (map :name m) m)
        dates (get-weekdays)]
        (loop [members members dates dates]
          (if-not (empty? dates) 
            (let [support (get-best-score members (first dates))
                  updated (update-support! (first dates) members (:pair (first support)))]
              (do (println support) (println updated) (println (map cc/to-long dates))
              (recur updated (rest dates))))))))

