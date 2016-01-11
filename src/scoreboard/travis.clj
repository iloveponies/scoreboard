(ns scoreboard.travis
  (:require [org.httpkit.client :as http]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join]]
            [scoreboard.util :as util]))

(def api-root "https://api.travis-ci.org")

(def api-headers {"Accept" (str "application/vnd.travis-ci.2+json"
                                ", application/json"
                                ", text/plain")})

(def api-user-agent "ILovePonies/1.0.0")

(def ->travis util/->rate-limited-pool)

(defn- rate-limit-reached? [headers]
  (contains? headers :retry-after))

(defn- rate-limit-reset [headers]
  (+ (System/currentTimeMillis)
     (* 1000 (Long/valueOf (:retry-after headers)))))

(defn- ->rate-limit-reached [url next-reset]
  {:error (format "Rate limit reached, next reset %s: %s"
                  (java.util.Date. next-reset) url)
   :next-reset next-reset})

(defn- ->error [status url body]
  {:error (format "Error %d, %s: %s"
                  status body url)})

(defn- throw-unexpected-error [error url]
  (throw (RuntimeException. (format "Unexpected error: %s" url)
                            error)))

(defn build [travis id]
  (letfn [(c []
            (let [url (join "/" [api-root "builds" id])
                  params {:user-agent api-user-agent
                          :headers api-headers}
                  {:keys [status headers body error]} @(http/get url params)]
              (when (some? error)
                (throw-unexpected-error error url))
              (let [result (cond (= 200 status)
                                 {:ok (:build (util/parse-json body))}
                                 (rate-limit-reached? headers)
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
    (util/submit travis c)))

(defn- min-build-number [builds]
  (->> builds
       (map #(Long/valueOf (:number %)))
       (apply min)))

(defn builds
  ([travis owner repository]
   (builds travis owner repository Long/MAX_VALUE))
  ([travis owner repository after]
   (letfn [(->ok [body]
             (let [bs (:builds (util/parse-json body))
                   min (if (empty? bs)
                         1
                         (min-build-number bs))]
               (if (< 1 min)
                 {:ok {:result bs
                       :next (fn [] (builds travis owner repository min))}}
                 {:ok {:result bs}})))
           (c []
             (let [url (join "/" [api-root "repos" owner repository "builds"])
                   params {:user-agent api-user-agent
                           :headers api-headers
                           :query-params {"event_type" "pull_request"
                                          "after_number" (str after)}}
                   {:keys [status headers body error]} @(http/get url params)]
               (when (some? error)
                 (throw-unexpected-error error url))
               (let [result (cond (= 200 status)
                                  (->ok body)
                                  (rate-limit-reached? headers)
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
     (util/submit travis c))))

(defn- parse-log-response [headers body]
  (if (.contains (:content-type headers)
                 "text/plain")
    body
    (get-in (util/parse-json body)
            [:log :body])))

(defn log [travis job-id]
  (letfn [(c []
            (let [url (join "/" [api-root "jobs" job-id "log"])
                  params {:user-agent api-user-agent
                          :headers api-headers}
                  {:keys [status headers body error]} @(http/get url params)]
              (when (some? error)
                (throw-unexpected-error error url))
              (let [result (cond (= 200 status)
                                 {:ok (parse-log-response headers body)}
                                 (rate-limit-reached? headers)
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
    (util/submit travis c)))

(defn notification-build [travis request]
  (match [(build travis (get-in request [:params :payload :id]))]
         [{:ok build}]
         (let [repository (get-in request [:params :payload :repository])]
           {:build build
            :owner (:owner-name repository)
            :name (:name repository)})
         [e] e))
