(ns scoreboard.scoreboard-test
  (:require [midje.sweet :refer :all]
            [scoreboard.scoreboard :refer :all]))

(def matti-ilp-td-1 (->score :user "Matti"
                             :exercise "ilp.td.1."
                             :points 1
                             :max-points 1))
(def teppo-ilp-td-1 (->score :user "Teppo"
                             :exercise "ilp.td.1."
                             :points 1
                             :max-points 1))
(def matti-ilp-td-1-no-points (->score :user "Matti"
                                       :exercise "ilp.td.1."
                                       :points 1
                                       :max-points 1))
(def matti-ilp-td-2 (->score :user "Matti"
                         :exercise "ilp.td.2."
                         :points 1
                         :max-points 1))
(def matti-ilp-sd-1 (->score :user "Matti"
                         :exercise "ilp.sd.1."
                         :points 1
                         :max-points 1))
(def matti-ilp-sd-1-no-points (->score :user "Matti"
                                       :exercise "ilp.sd.1."
                                       :points 0
                                       :max-points 1))

(fact "add-score"
  (let [no-score (->score :user "Matti"
                          :exercise "ilp.td.1"
                          :points 0
                          :max-points 1)
        score (->score :user "Matti"
                       :exercise "ilp.td.1"
                       :points 1
                       :max-points 1)]
    (add-score #{} no-score)
    => #{no-score}
    (add-score #{no-score} score)
    => (contains score)))

(fact "score-by-user-exercise"
  (let [score (->score :user "Matti"
                       :exercise "ilp.td.1"
                       :points 1
                       :max-points 1)
        no-score (->score :user "Matti"
                          :exercise "ilp.td.1"
                          :points 0
                          :max-points 1)
        wrong-exercise (->score :user "Matti"
                                :exercise "ilp.td.2"
                                :points 0
                                :max-points 1)
        wrong-user (->score :user "Teppo"
                            :exercise "ilp.td.1"
                            :points 0
                            :max-points 1)]
    (score-by-user-exercise (->scoreboard) "Matti" "ilp.td.1")
    => nil?
    (score-by-user-exercise (add-score (->scoreboard) score)
                            "Matti" "ilp.td.1")
    => score
    (score-by-user-exercise (-> (->scoreboard)
                                (add-score wrong-exercise))
                            "Matti" "ilp.td.1")
    => nil?
    (score-by-user-exercise (-> (->scoreboard)
                                (add-score wrong-user))
                            "Matti" "ilp.td.1")
    => nil?
    (score-by-user-exercise (-> (->scoreboard)
                                (add-score score)
                                (add-score wrong-exercise))
                            "Matti" "ilp.td.1")
    => score
    (score-by-user-exercise (-> (->scoreboard)
                                (add-score score)
                                (add-score wrong-user))
                            "Matti" "ilp.td.1")
    => score
    (score-by-user-exercise (-> (->scoreboard)
                                (add-score score)
                                (add-score no-score))
                            "Matti" "ilp.td.1")
    => score))

(fact "max-score"
  (let [score (->score :user "Matti"
                       :exercise "ilp.td.1"
                       :points 1
                       :max-points 1)
        no-score (->score :user "Matti"
                          :exercise "ilp.td.1"
                          :points 0
                          :max-points 1)]
    (max-score score no-score)
    => score))

(fact "sum-score"
  (let [score-1 (->score :user "Matti"
                         :exercise "ilp.td.1"
                         :points 1
                         :max-points 1)
        score-2 (->score :user "Matti"
                         :exercise "ilp.td.2"
                         :points 1
                         :max-points 1)
        score-3 (->score :user "Teppo"
                         :exercise "ilp.td.3"
                         :points 1
                         :max-points 1)]
    (sum-score score-1 score-2)
    => (->score :user "Matti"
                :exercise "ilp.td.1"
                :points 2
                :max-points 2)
    (sum-score score-3 score-1)
    => (->score :user "Teppo"
                :exercise "ilp.td.3"
                :points 2
                :max-points 2)
    (sum-score score-1 score-2 score-3)
    => (->score :user "Matti"
                :exercise "ilp.td.1"
                :points 3
                :max-points 3)))

(fact "truncate-to-level"
  (truncate-to-level "ilp.td.1." 2)
  => "ilp.td.1."
  (truncate-to-level "ilp.td.1." 1)
  => "ilp.td."
  (truncate-to-level "ilp.td.1." 0)
  => "ilp.")

(fact "group-at-level"
  (group-at-level [matti-ilp-td-1] 2)
  => {"ilp.td.1." [matti-ilp-td-1]}
  (group-at-level [matti-ilp-td-1] 1)
  => {"ilp.td." [matti-ilp-td-1]}
  (group-at-level [matti-ilp-td-1 matti-ilp-td-2] 2)
  => {"ilp.td.1." [matti-ilp-td-1]
      "ilp.td.2." [matti-ilp-td-2]})

(fact "total-score-at-level"
  (total-scores-at-level (add-score (->scoreboard) matti-ilp-td-1) 0)
  => #{(assoc matti-ilp-td-1
         :exercise "ilp.")}
  (total-scores-at-level (add-score (->scoreboard) matti-ilp-td-1) 1)
  => #{(assoc matti-ilp-td-1
         :exercise "ilp.td.")}
  (total-scores-at-level (add-score (->scoreboard) matti-ilp-td-1) 2)
  => #{matti-ilp-td-1}
  (total-scores-at-level (add-score (->scoreboard) matti-ilp-td-1) 3)
  => #{matti-ilp-td-1}
  (total-scores-at-level (->scoreboard) 0)
  => empty?
  (total-scores-at-level (->scoreboard) 1)
  => empty?
  (total-scores-at-level (-> (->scoreboard)
                            (add-score matti-ilp-td-1)
                            (add-score matti-ilp-td-2))
                        1)
  => #{(assoc (sum-score matti-ilp-td-1 matti-ilp-td-2)
         :exercise "ilp.td.")}
  (total-scores-at-level (-> (->scoreboard)
                            (add-score matti-ilp-td-1)
                            (add-score matti-ilp-td-2))
                        0)
  => #{(assoc (sum-score matti-ilp-td-1 matti-ilp-td-2)
         :exercise "ilp.")}
  (total-scores-at-level (-> (->scoreboard)
                            (add-score matti-ilp-td-1)
                            (add-score matti-ilp-td-1-no-points))
                        1)
  => #{(assoc (max-score matti-ilp-td-1 matti-ilp-td-1-no-points)
         :exercise "ilp.td.")}
  (total-scores-at-level (-> (->scoreboard)
                            (add-score matti-ilp-td-1)
                            (add-score teppo-ilp-td-1))
                        1)
  => #{(assoc matti-ilp-td-1
         :exercise "ilp.td.")
       (assoc teppo-ilp-td-1
         :exercise "ilp.td.")})
