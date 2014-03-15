(ns scoreboard.score)

(defprotocol ScoreStore)

(defrecord Score [user points time problem-key])
