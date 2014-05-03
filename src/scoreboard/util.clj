(ns scoreboard.util)

(defn retrying [f between-retries]
  (fn [& args]
    (loop [between-retries between-retries]
      (if-let [r (try [(apply f args)]
                      (catch Exception e
                        (when (empty? between-retries)
                          (throw e))))]
        (r 0)
        (do (Thread/sleep (first between-retries))
            (recur (rest between-retries)))))))
