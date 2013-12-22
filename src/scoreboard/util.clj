(ns scoreboard.util
  (:require [clj-http.client :as http]))

(defn retry [retries on-fail thunk]
  (if-let [result (try
                    [(thunk)]
                    (catch Exception e
                      (when (empty? retries)
                        (throw e))))]
    (result 0)
    (do (on-fail)
        (Thread/sleep (first retries))
        (recur (rest retries) on-fail thunk))))

(defn retrying-http [method url parameters retries]
  (let [methods {:get http/get
                 :post http/post}]
    (retry retries
           #(println "http request to" url "failed")
           #((get methods method) url parameters))))
