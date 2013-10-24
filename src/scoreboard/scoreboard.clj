(ns scoreboard.scoreboard)

(defn ->score [& {:keys [user exercise points max-points] :as s}]
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

(defn score-by-user-exercise [scoreboard user exercise]
  (first (clojure.set/select (fn [score]
                   (and (= user (:user score))
                        (= exercise (:exercise score))))
                 scoreboard)))

(defn is-prefix-of? [predix-seq seq]
  (cond (empty? predix-seq) true
        (empty? seq) false
        :else (and (= (first predix-seq) (first seq))
                   (is-prefix-of? (rest predix-seq)
                                  (rest seq)))))

(defn scores-at-level [scoreboard level]
  (clojure.set/select (fn [score]
                        (or (empty? level)
                            (is-prefix-of? (.split level "\\.")
                                           (.split (:exercise score) "\\."))))
                      scoreboard))

(defn total-score-at-level [scoreboard level]
  (set
   (for [[_ scores] (clojure.set/index scoreboard [:user])
         :let [scores (scores-at-level scores level)]
         :when (not (empty? scores))]
     (assoc (apply sum-score scores)
       :exercise level))))

(defn add-score [scoreboard score]
  (if-let [previous-score (score-by-user-exercise scoreboard
                                                  (:user score)
                                                  (:exercise score))]
    (-> (disj scoreboard previous-score)
        (conj (max-score previous-score score)))
    (conj scoreboard score)))
