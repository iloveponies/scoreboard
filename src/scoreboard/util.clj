(ns scoreboard.util
  (:require [cheshire.core :as json]
            [clojure.core.match :refer [match]])
  (:import
   [java.util.concurrent
    ThreadPoolExecutor
    TimeUnit
    Callable
    SynchronousQueue
    ExecutionException
    RejectedExecutionException]))

(defn parse-json [s]
  (json/parse-string s (fn [k] (keyword (.replace k "_" "-")))))

(defn ->rate-limited-pool [number-of-concurrent-request]
  {:pool (ThreadPoolExecutor. number-of-concurrent-request
                              number-of-concurrent-request
                              1 TimeUnit/SECONDS
                              (SynchronousQueue.))
   :next-reset (atom 0)})

(defn- rate-limit-reached [next-reset]
  {:rate-limit-reached (format "Rate limit has been reached. Next reset is %s."
                               (java.util.Date. next-reset))
   :next-reset next-reset})

(defn- concurrency-limit-reached [concurrency-limit]
  {:concurrency-limit-reached (format "Concurrency limit of %d has been reached."
                                      concurrency-limit)
   :concurrency-limit concurrency-limit})

(defn submit [{:keys [pool next-reset]} task]
  (let [nr next-reset
        next-reset @next-reset
        c (fn []
            (let [{:keys [rate-limit-reached? next-reset result]} (task)]
              (when rate-limit-reached?
                (swap! nr (constantly next-reset)))
              result))]
    (if (< (System/currentTimeMillis) next-reset)
      (rate-limit-reached next-reset)
      (try
        (.get (.submit #^java.util.concurrent.AbstractExecutorService pool
                       #^java.util.concurrent.Callable c)
              1 TimeUnit/MINUTES)
        (catch ExecutionException e
          (throw (.getCause e)))
        (catch RejectedExecutionException e
          (concurrency-limit-reached (.getMaximumPoolSize pool)))))))

(defn try-times [n f]
  (loop [n 3]
    (match [(f)]
           [{:ok result}]
           result
           [{:error msg
             :next-reset next-reset}]
           (do (println msg)
               (Thread/sleep (- next-reset (System/currentTimeMillis)))
               (recur n))
           [{:error msg}]
           (if (< 0 n)
             (do (println msg)
                 (Thread/sleep 2000)
                 (recur (dec n)))
             (throw (RuntimeException. msg))))))
