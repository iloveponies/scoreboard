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
  {:error (format "Rate limit has been reached. Next reset is %s."
                  (java.util.Date. next-reset))
   :next-reset next-reset})

(defn- concurrency-limit-reached [concurrency-limit]
  {:error (format "Concurrency limit of %d has been reached."
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

(defn try-times [times f]
  (loop [n times]
    (match [(f)]
           [{:ok {:result result
                  :next next}}]
           {:result result
            :next (fn [] (try-times times next))}
           [{:ok result}]
           result
           [({:error msg} :as e)]
           (if (< 0 n)
             (do (println msg)
                 (Thread/sleep
                  (if-let [next-reset (:next-reset e)]
                    next-reset
                    2000))
                 (recur (dec n)))
             (throw (RuntimeException. msg))))))

(defn collect [aggregate initial f]
  (loop [results initial
         thunk f]
    (match [(thunk)]
           [{:result result
             :next next}]
           (recur (aggregate results result)
                  next)
           [{:result result}]
           (aggregate results result))))
