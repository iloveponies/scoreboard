(ns scoreboard.board)

(defprotocol Store
  (insert! [store board])
  (by-name-and-parent-key! [store name parent-key])
  (by-ancestry! [store name & ancestors]))

(defrecord Board [name parent-key])
