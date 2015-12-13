(ns scoreboard.github
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as a]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join split]]
            [scoreboard.util :as util]))

(def api-root "https://api.github.com")

(def auth (System/getenv "GITHUB_AUTH"))

(defn ->github [number-of-concurrent-request]
  (let [throttle (a/chan number-of-concurrent-request)]
    (a/onto-chan throttle (repeat number-of-concurrent-request :ok) false)
    throttle))

(defn- rate-limit-reached? [headers]
  (= "0" (:x-ratelimit-remaining headers)))

(defn- rate-limit-reset [headers]
  (* 1000 (Long/valueOf (:x-ratelimit-reset headers))))

(defn- throttle-if-needed [throttle headers]
  (a/go
    (when (rate-limit-reached? headers)
      (let [reset (rate-limit-reset headers)]
        (println "rate limit reached, reset at"
                 (new java.util.Date reset))
        (a/<! (a/timeout (- reset (System/currentTimeMillis))))))
    (a/>! throttle :ok)))

(defn pull-request [throttle owner repository number out]
  (a/go
    (a/<! throttle)
    (http/get (join "/" [api-root "repos" owner repository "pulls" number])
              (if auth {:basic-auth (split auth #":")} {})
              (fn [{:keys [status headers body error]}]
                (a/go
                  (a/>! out
                        (cond error
                              (RuntimeException.
                               (str "fetching pull request "
                                    owner "/" repository "/" number " failed")
                               error)
                              (= 200 status)
                              (util/parse-json body)
                              :else
                              (RuntimeException.
                               (str "fetching pull request "
                                    owner "/" repository "/" number
                                    " failed: "
                                    (:message (util/parse-json body))))))
                  (a/close! out)
                  (throttle-if-needed throttle headers)))))
  out)

(defn pull-request-author [throttle owner repository number out]
  (a/go
    (a/>! out
          (try
            (get-in (util/<? (pull-request throttle owner repository number (a/chan 1)))
                    [:user :login])
            (catch Exception e
              (RuntimeException.
               (str "fetching author of pull request "
                    owner "/" repository "/" number " failed")
               e))))
    (a/close! out))
  out)
