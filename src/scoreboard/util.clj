(ns scoreboard.util
  (:require [clojure.core.async :as a]
            [clojure.core.match :refer [match]]
            [cheshire.core :as json]))

(defn parse-json [s]
  (json/parse-string s (fn [k] (keyword (.replace k "_" "-")))))

(defn throw-err [e]
  (when (instance? Throwable e) (throw e))
  e)

(defmacro <? [channel]
  `(throw-err (a/<! ~channel)))

(defn try-times [n f out]
  (a/go-loop [n n]
    (let [c (f (a/chan))]
      (if-let [error (loop []
                       (when-let [m (a/<! c)]
                         (if (instance? Throwable m)
                           m
                           (do (a/>! out m)
                               (recur)))))]
        (if (< 0 n)
          (recur (dec n))
          (do (a/>! out error)
              (a/close! out)))
        (a/close! out))))
  out)
