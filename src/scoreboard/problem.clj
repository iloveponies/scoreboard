(ns scoreboard.problem)

(defprotocol Store
  (insert! [store problem]))

(defrecord Problem [name max-points board-key])
