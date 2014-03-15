(ns scoreboard.scoreboard
  (:require [clojure.set])
  (:require [scoreboard.score :as score]
            [scoreboard.problem :as problem]))

(defn ->Board
  ([{:keys [name]}]
     (board/->Board name nil))
  ([board {:keys [name]}]
     (board/->Board name (:key board))))

(defn ->Problem [board {:keys [name max-points]}]
  (problem/->Problem name max-points (:key board)))

(defn ->Score [problem {:keys [user points time]}]
  (when (<= points (:max-points (:value problem)))
    (score/->Score user points time (:key problem))))

(defn ->score [& {:keys [user repo exercise points max-points] :as s}]
  s)

(defn ->scoreboard []
  #{})

(defn max-score
  ([score] score)
  ([score score']
     (assoc score
       :points (max (:points score) (:points score'))
       :max-points (max (:max-points score) (:max-points score'))))
  ([score score' & r]
     (reduce max-score (max-score score score') r)))

(defn sum-score
  ([score] score)
  ([score score']
     (assoc score
       :points (+ (:points score) (:points score'))
       :max-points (+ (:max-points score) (:max-points score'))))
  ([score score' & r]
     (reduce sum-score (sum-score score score') r)))

(defn one-by-score [scoreboard score]
  (first (clojure.set/select (fn [{:keys [user repo exercise]}]
                               (and (= user (:user score))
                                    (= repo (:repo score))
                                    (= exercise (:exercise score))))
                             scoreboard)))

(defn select-by-repo [scoreboard repo]
  (clojure.set/select (fn [score] (= repo (:repo score))) scoreboard))

(defn select-by-user [scoreboard user]
  (clojure.set/select (fn [score] (= user (:user score))) scoreboard))

(defn total-scores [scoreboard]
  (set
   (for [[_ scores] (group-by :user scoreboard)
         :when (not (empty? scores))]
     (apply sum-score scores))))

(defn total-scores-by-repo [scoreboard repo]
  (total-scores (select-by-repo scoreboard repo)))

(defn user-scores [scoreboard user]
  (set
   (for [[_ scores] (group-by :repo (select-by-user scoreboard user))
         :when (not (empty? scores))]
     (apply sum-score scores))))

(defn add-score [scoreboard score]
  (if-let [previous-score (one-by-score scoreboard score)]
    (-> (disj scoreboard previous-score)
        (conj (max-score previous-score score)))
    (conj scoreboard score)))
