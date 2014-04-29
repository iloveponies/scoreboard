(ns scoreboard.score)

(defprotocol Store
  (insert! [store score]))

(defrecord Score [user points time problem-key])

(defn ->Score [user points time problem-entity]
  (when (<= points (get-in problem-entity [:value :max-points]))
    (Score. user points time (:key problem-entity))))
