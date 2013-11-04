(ns scoreboard.util)

(defn try-times [n thunk]
  (loop [retry-in 1000
         n n]
    (if-let [result (try
                      [(thunk)]
                      (catch Exception e
                        (when (zero? n)
                          (throw e))))]
      (result 0)
      (do (println "retrying in" retry-in)
          (Thread/sleep retry-in)
          (recur (* retry-in 2)
                 (dec n))))))
