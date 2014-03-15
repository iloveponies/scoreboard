(ns scoreboard.score)

(defprotocol Store
  (insert! [store score]))

(defrecord Score [user points time problem-key])
