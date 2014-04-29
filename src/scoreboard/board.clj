(ns scoreboard.board)

(defprotocol Store
  (insert! [store board])
  (get! [store name parent-key])
  (total! [store name parent-key]))

(defrecord Board [name parent-key])
