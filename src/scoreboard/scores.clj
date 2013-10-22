(ns scoreboard.scores)

;;{
;; "user1" {:1 {:1.1 {}}}
;; "user2" {:1 {:1.1 {}}}
;;}
(defn ->in-memory-scores []
  (atom {}))

(defn total-exercise-score [s]
  (apply merge-with + (vals s)))

(defn total-user-score [s]
  (apply merge-with + (map total-exercise-score (vals s))))

(defn get-scores
  ([scores]
     (map (fn [[user user-score]]
            {user (total-user-score user-score)})
          @scores))
  ([scores exercise-set]
     (map (fn [[user user-score]]
            {user (total-exercise-score (get user-score exercise-set))})
          @scores)))

(defn update-score! [scores author-name exercise-set exercise score]
  (swap! scores
         update-in [author-name exercise-set exercise]
         (fn [points]
           (merge-with max points score))))
