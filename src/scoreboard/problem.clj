(ns scoreboard.problem)

(defprotocol Store
  (insert! [store problem])
  (get! [store name board-key])
  (best-scores! [store name board-key]))

(defrecord Problem [name max-points board-key])
