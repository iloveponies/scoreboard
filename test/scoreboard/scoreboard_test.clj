(ns scoreboard.scoreboard-test
  (:require [midje.sweet :refer :all]
            [scoreboard.scoreboard :refer :all]))

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

(fact "scores-at-level"
  (let [score-1 (->score :user "Matti"
                         :exercise "ilp.td.1"
                         :points 1
                         :max-points 1)
        score-2 (->score :user "Matti"
                         :exercise "ilp.td.2"
                         :points 1
                         :max-points 1)]
  (scores-at-level (add-score (->scoreboard) score-1) "ilp.td.1")
  => (add-score (->scoreboard) score-1)
  (scores-at-level (add-score (->scoreboard) score-1) "ilp.td.2")
  => (->scoreboard)
  (scores-at-level (-> (->scoreboard)
                       (add-score score-1)
                       (add-score score-2))
                   "ilp.td.1")
  => (add-score (->scoreboard) score-1)
  (scores-at-level (-> (->scoreboard)
                       (add-score score-1)
                       (add-score score-2))
                   "ilp.td")
  => (-> (->scoreboard)
         (add-score score-1)
         (add-score score-2))))

(fact "total-score-at-level"
  (let [score-1 (->score :user "Matti"
                         :exercise "ilp.td.1"
                         :points 1
                         :max-points 1)
        no-score-1 (->score :user "Matti"
                            :exercise "ilp.td.1"
                            :points 0
                            :max-points 1)
        score-2 (->score :user "Matti"
                         :exercise "ilp.td.2"
                         :points 1
                         :max-points 1)
        score-1+2 (->score :user "Matti"
                           :exercise "ilp.td"
                           :points 2
                           :max-points 2)
        score-3 (->score :user "Matti"
                         :exercise "ilp.sd.1"
                         :points 1
                         :max-points 1)]
    (total-score-at-level (->scoreboard) "ilp.td")
    => empty?
    (total-score-at-level (-> (->scoreboard)
                              (add-score score-1)
                              (add-score score-2))
                          "ilp.td")
    => #{score-1+2}
    (total-score-at-level (-> (->scoreboard)
                              (add-score score-1)
                              (add-score no-score-1))
                          "ilp.td")
    => #{(assoc score-1 :exercise "ilp.td")}
    (total-score-at-level (-> (->scoreboard)
                              (add-score (assoc score-1
                                           :exercise "foo.td.1"))
                              (add-score (assoc score-2
                                           :exercise "foo.td.2")))
                          "foo.td")
    => #{(->score :user "Matti"
                  :exercise "foo.td"
                  :points 2
                  :max-points 2)}
    (total-score-at-level (-> (->scoreboard)
                              (add-score score-1)
                              (add-score (assoc score-1 :user "Teppo")))
                          "ilp.td")
    => #{(->score :user "Matti"
                  :exercise "ilp.td"
                  :points 1
                  :max-points 1)
         (->score :user "Teppo"
                  :exercise "ilp.td"
                  :points 1
                  :max-points 1)}
    (total-score-at-level (-> (->scoreboard)
                              (add-score score-1)
                              (add-score score-3))
                          "ilp.td")
    => #{(assoc score-1 :exercise "ilp.td")}
    (total-score-at-level (-> (->scoreboard)
                              (add-score score-1)
                              (add-score score-2)
                              (add-score score-3))
                          "ilp.td")
    => #{(->score :user "Matti"
                  :exercise "ilp.td"
                  :points 2
                  :max-points 2)}))
