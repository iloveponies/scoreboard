(ns scoreboard.problem)

(defprotocol ProblemStore)

(defrecord Problem [name max-points board-key])
