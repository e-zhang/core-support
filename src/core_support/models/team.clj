(ns core-support.models.team
    (:require [monger.core :as mg]
              [monger.collection :as mc]
              [monger.result :as mr]
              [monger.operators :refer :all]
              [clojure.pprint :as pp]
              [clj-time.core :as ct]
              [clj-time.coerce :as cc]
              [clj-time.periodic :as cp]
              [clj-time.predicates :as cd]
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

(defn today? [day]
	(cd/same-date? (cf/parse (cf/formatter "yyyy-MM-dd") day) (ct/today-at-midnight)))

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


(defn reset-docs! []
  (if-not (mr/ok? (mc/update team-collection {:lastdate { $exists true } :lastpartner { $exists true }}
                                             {$set {:lastpartner nil :lastdate (cc/to-long (ct/epoch))}} :multi true))
    (valid/set-error :reset "Failed to reset dates and partners")
    (valid/set-error :reset "Successfully reset dates and partners")))


(defn update-support! [date m [p1 p2 :as pair]]
  (let [datestring (cf/unparse (cf/formatter "yyyy-MM-dd") date)]
    (if-not (and (mr/ok? (mc/update team-collection {:name p1} {$set {:lastpartner p2 :lastdate (cc/to-long date)}}))
                 (mr/ok? (mc/update team-collection {:name p2} {$set {:lastpartner p1 :lastdate (cc/to-long date)}}))
                 (mr/ok? (mc/update schedule-collection {:day datestring} {$set {:primary p1 :secondary p2}} :upsert true)))
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
       (map #(* % 5))
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
    (+ (if (some #(= % "execution") (map :project members)) 6 0)
       (get-time-score date (map :lastdate members))
       (get-partner-score members)))) 

(defn get-scores [members date]
  (->> (comb/combinations (keys members) 2)
       (map #(assoc {} :pair % :score (score date (vals (select-keys members %)))))
       (sort-by :score >)))


(defn get-best-score [scores]
	(let [best (:score (first scores))]
		(rand-nth (take-while #(>= (:score %) best) scores))))


(defn get-weekdays []
  (let [today (ct/today-at-midnight)
	    start (ct/minus today (ct/days (ct/day-of-week today)))]
	  (->> (cp/periodic-seq start (ct/days 1)) 
		   (drop-while cd/weekend?)
		   (take-while cd/weekday?))))


(defn get-weekly-schedule! []
  (let [m (get-all-members)
        members (zipmap (map :name m) m)
        dates (get-weekdays)]
        (loop [members members dates dates]
          (if (empty? dates) 
	    (valid/set-error :recalc "Recalced support schedule")
            (let [support (get-scores members (first dates))
                  updated (update-support! (first dates) members (:pair (get-best-score support)))]
              (do (pp/pprint support) (pp/pprint updated) (println (map cc/to-long dates)) (println "---------------")
              (recur updated (rest dates))))))))


(defn get-partner [m schedule]
	(cond 
		(= (:primary schedule) m) (:secondary schedule)
		(= (:secondary schedule) m) (:primary schedule)))

(defn get-self [m schedule]
	(cond 
		(= (:primary schedule) m) :primary
		(= (:secondary schedule) m) :secondary))
			
(defn set-schedule! [swap]
	(if-not (every? true? (map (partial contains? swap) [:from  :to :to-date]))
		(valid/set-error :swap "Could not swap, missing details")
		(let [ end  (mc/find-one-as-map schedule-collection {:day (:to-date swap)})
			  end-date (cf/parse (cf/formatter "yyyy-MM-dd") (:to-date swap))
			  p1 (:from swap)
			  p2 (:to swap)
			  p2-partner (get-partner p1 end)
			  p2-self (get-self p1 end)]
			(if (some false? [(not (nil? p2-partner))  (not (nil? p2-self))
				(mr/ok? (mc/update team-collection {:name p2 :lastdate {:$lt (cc/to-long end-date)}} 
													{$set {:lastpartner p2-partner :lastdate (cc/to-long end-date)}}))
				(mr/ok? (mc/update team-collection {:name p2-partner :lastdate (cc/to-long end-date)}
												   {$set {:lastpartner p2}}))
				(mr/ok? (mc/update schedule-collection {:day (:to-date swap)} {$set {p2-self p2}}))]) 
				(valid/set-error :swap (apply str "Error swapping " p1 p2)))
				(valid/set-error :swap "SWAP SUCCESS"))))

(defn swap-schedule! [swap]
	(if-not (every? true? (map (partial contains? swap) [:from :from-date :to :to-date]))
		(valid/set-error :swap "Could not swap, missing details")
		(let [start (mc/find-one-as-map schedule-collection {:day (:from-date swap)})
			  end  (mc/find-one-as-map schedule-collection {:day (:to-date swap)})
			  start-date (cf/parse (cf/formatter "yyyy-MM-dd") (:from-date swap))
			  end-date (cf/parse (cf/formatter "yyyy-MM-dd") (:to-date swap))
			  p1 (:from swap)
			  p2 (:to swap)
			  p1-partner (get-partner p2 end)
			  p2-partner (get-partner p1 start)
			  p1-self (get-self p2 end)
			  p2-self (get-self p1 start)]
			(if (some false? [(not (nil? p1-partner)) (not (nil? p2-partner)) (not (nil? p1-self)) (not (nil? p2-self))
				(mr/ok? (mc/update team-collection {:name p1 :lastdate {:$lt (cc/to-long end-date)}} 
												   {$set {:lastpartner p1-partner :lastdate (cc/to-long end-date)}}))
				(mr/ok? (mc/update team-collection {:name p1-partner :lastdate (cc/to-long end-date)}
												   {$set {:lastpartner p1}}))
				(mr/ok? (mc/update team-collection {:name p2 :lastdate {:$lt (cc/to-long start-date)}} 
													{$set {:lastpartner p2-partner :lastdate (cc/to-long start-date)}}))
				(mr/ok? (mc/update team-collection {:name p2-partner :lastdate (cc/to-long start-date)}
												   {$set {:lastpartner p2}}))
				(mr/ok? (mc/update schedule-collection {:day (:to-date swap)} {$set {p1-self p1}})) 
				(mr/ok? (mc/update schedule-collection {:day (:from-date swap)} {$set {p2-self p2}}))])
				(valid/set-error :swap (apply str "Error swapping " p1 p2)))
				(valid/set-error :swap "SWAP SUCCESS"))))
		
