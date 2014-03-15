(ns scoreboard.board)

(defprotocol Store
  (insert! [store board]))

(defrecord Board [name parent-key])
