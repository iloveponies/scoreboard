(ns scoreboard.board)

(defprotocol BoardStore)

(defrecord Board [name parent-key])
