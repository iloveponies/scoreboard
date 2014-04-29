(ns scoreboard.in-memory-store
  (:require [scoreboard.board :as board]
            [scoreboard.problem :as problem]
            [scoreboard.score :as score]))

(deftype InMemoryStore [score-table problem-table board-table])

(defn ->InMemoryStore []
  (InMemoryStore. (ref #{}) (ref #{}) (ref #{})))

(defn- map-vals [f hash-map]
  (reduce (fn [m [k v]] (assoc m k (f v))) {} hash-map))

(defn- first-matching [xset & preds]
  (first (clojure.set/select (fn [e] (every? (fn [pred] (pred e)) preds))
                             xset)))

(defn- child-scores [store problem-key]
  (filter #(= problem-key (:problem-key %)) @(.score-table store)))

(defn- child-problems [store board-key]
  (filter #(= board-key (:board-key %)) @(.problem-table store)))

(defn- child-boards [store board-key]
  (filter #(= board-key (:parent-key %)) @(.board-table store)))

(defn- all-problems [store board-key]
  (apply concat
         (child-problems store board-key)
         (map (partial all-problems store)
              (child-boards store board-key))))

(defn- best-scores [store problem-key]
  (->> (child-scores store problem-key)
       (group-by :user)
       (map-vals (partial apply max-key :points))))

(extend-type InMemoryStore
  board/Store
  (insert! [store board]
    (alter (.board-table store) conj board)
    board)

  (get! [store name parent-key]
    (when-let [b (first-matching @(.board-table store)
                                 #(= name (:name %))
                                 #(= parent-key (:parent-key %)))]
      {:key b :value b}))

  (total! [store name parent-key]
    (when-let [problems (some->> (board/get! store name parent-key)
                                 :key
                                 (all-problems store))]
      {:points (->> (map (partial best-scores store) problems)
                    (map (partial map-vals :points))
                    (apply merge-with + {}))
       :max-points (apply + 0 (map :max-points problems))}))

  problem/Store
  (insert! [store problem]
    (alter (.problem-table store) conj problem)
    problem)

  (get! [store name board-key]
    (when-let [p (first-matching @(.problem-table store)
                                 #(= name (:name %))
                                 #(= board-key (:board-key %)))]
      {:key p :value p}))

  (best-scores! [store name board-key]
    (when-let [problem (problem/get! store name board-key)]
      (best-scores store (:key problem))))

  score/Store
  (insert! [store score]
    (alter (.score-table store) conj score)
    score))
