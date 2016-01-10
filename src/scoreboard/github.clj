(ns scoreboard.github
  (:require [org.httpkit.client :as http]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join split]]
            [scoreboard.util :as util]))

(def auth (System/getenv "GITHUB_AUTH"))

(def api-root "https://api.github.com")

(def api-params (if auth {:basic-auth (split auth #":")} {}))

(defn- rate-limit-reached? [headers]
  (= "0" (:x-ratelimit-remaining headers)))

(defn- rate-limit-reset [headers]
  (* 1000 (Long/valueOf (:x-ratelimit-reset headers))))

(def ->github util/->rate-limited-pool)

(defn- ->ok [body]
  {:ok (util/parse-json body)})

(defn- ->rate-limit-reached [url next-reset]
  {:error (format "Rate limit reached, next reset %s: %s"
                  (java.util.Date. next-reset) url)
   :next-reset next-reset})

(defn- ->error [status url body]
  {:error (format "Error %d, %s: %s"
                  status (:message (util/parse-json body)) url)})

(defn- throw-unexpected-error [error url]
  (throw (RuntimeException. (format "Unexpected error: %s" url)
                            error)))

(defn pull-request [github owner repository number]
  (letfn [(c []
            (let [url (join "/" [api-root "repos" owner repository "pulls" number])
                  {:keys [status headers body error]} @(http/get url api-params)]
              (when (some? error)
                (throw-unexpected-error error url))
              (let [result (cond (= 200 status)
                                 (->ok body)
                                 (and (= 403 status) (rate-limit-reached? headers))
                                 (->rate-limit-reached
                                  url
                                  (rate-limit-reset headers))
                                 :else
                                 (->error status url body))]
                (if (rate-limit-reached? headers)
                  {:rate-limit-reached? true
                   :next-reset (rate-limit-reset headers)
                   :result result}
                  {:rate-limit-reached? false
                   :result result}))))]
    (util/submit github c)))

(defn pull-request-author [github owner repository number]
  (match [(pull-request github owner repository number)]
         [{:ok {:user {:login author}}}] {:ok author}
         [e] e))
