(ns scoreboard.board)

(defprotocol Store
  (insert! [store board])
  (by-name-and-parent-key! [store name parent-key]))

(defrecord Board [name parent-key])

(defn by-ancestry! [store name & ancestors]
  (if (empty? ancestors)
    (by-name-and-parent-key! store name nil)
    (when-let [parent (apply by-ancestry!
                             store
                             (first ancestors)
                             (rest ancestors))]
      (by-name-and-parent-key! store name (:key parent)))))
