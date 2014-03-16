(ns scoreboard.problem)

(defprotocol Store
  (insert! [store problem])
  (by-name-and-board! [store name board]))

(defrecord Problem [name max-points board-key])
