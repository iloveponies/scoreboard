(ns scoreboard.scores-test
  (:use scoreboard.scores
        midje.sweet))

(fact "total-exercise-score"
  (total-exercise-score {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 1 :out-of 1}})
  => {:got 2 :out-of 2}
  (total-exercise-score {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 0 :out-of 1}})
  => {:got 1 :out-of 2})

(fact "total-user-score"
  (total-user-score {:1 {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 1 :out-of 1}}
                     :2 {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 1 :out-of 1}}})
  => {:got 4 :out-of 4}
  (total-user-score {:1 {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 1 :out-of 1}}
                     :2 {:1.1 {:got 1 :out-of 1}
                         :1.2 {:got 0 :out-of 1}}})
  => {:got 3 :out-of 4})

(fact "get-scores"
  (let [scores  (atom {"user1" {:1 {:1.1 {:got 1 :out-of 1}
                                    :1.2 {:got 1 :out-of 1}}
                                :2 {:1.1 {:got 1 :out-of 1}
                                    :1.2 {:got 1 :out-of 1}}}
                       "user2" {:1 {:1.1 {:got 1 :out-of 1}
                                    :1.2 {:got 1 :out-of 1}}
                                :2 {:1.1 {:got 1 :out-of 1}
                                    :1.2 {:got 0 :out-of 1}}}})]
    (get-scores scores "user1")
    => {:got 4 :out-of 4}
    (get-scores scores "user2")
    => {:got 3 :out-of 4}
    (get-scores scores "user1" :1)
    => {:got 2 :out-of 2}
    (get-scores scores "user2" :2)
    => {:got 1 :out-of 2}
    (get-scores scores "user1" :1 :1.1)
    => {:got 1 :out-of 1}
    (get-scores scores "user2" :2 :1.2)
    => {:got 0 :out-of 1}))

(fact "update-score"
  (let [empty-scores (->in-memory-scores)
        full-scores (atom {"user1" {:1 {:1.1 {:got 1 :out-of 1}
                                        :1.2 {:got 1 :out-of 1}}
                                    :2 {:1.1 {:got 1 :out-of 1}
                                        :1.2 {:got 1 :out-of 1}}}
                           "user2" {:1 {:1.1 {:got 1 :out-of 1}
                                        :1.2 {:got 1 :out-of 1}}
                                    :2 {:1.1 {:got 1 :out-of 1}
                                        :1.2 {:got 0 :out-of 1}}}})]
    (update-score! empty-scores "user1" :1 :1.1 {:got 1 :out-of 1})
    (update-score! full-scores "user2" :2 :1.2 {:got 1 :out-of 1})
    @empty-scores
    => {"user1" {:1 {:1.1 {:got 1 :out-of 1}}}}
    @full-scores
    => {"user1" {:1 {:1.1 {:got 1 :out-of 1}
                     :1.2 {:got 1 :out-of 1}}
                 :2 {:1.1 {:got 1 :out-of 1}
                     :1.2 {:got 1 :out-of 1}}}
        "user2" {:1 {:1.1 {:got 1 :out-of 1}
                     :1.2 {:got 1 :out-of 1}}
                 :2 {:1.1 {:got 1 :out-of 1}
                     :1.2 {:got 1 :out-of 1}}}}))
