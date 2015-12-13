(ns scoreboard.travis
  (:require [org.httpkit.client :as http]
            [clojure.core.async :as a]
            [clojure.string :refer [join]]
            [scoreboard.util :as util]))

(def api-root "https://api.travis-ci.org")

(def api-headers {"Accept" (str "application/vnd.travis-ci.2+json"
                                ", application/json"
                                ", text/plain")})

(def api-user-agent "ILovePonies/1.0.0")

(defn ->travis [number-of-concurrent-request]
  (let [throttle (a/chan number-of-concurrent-request)]
    (a/onto-chan throttle (repeat number-of-concurrent-request :ok) false)
    throttle))

(defn- throttle-if-needed [throttle headers]
  (a/go
    (when (contains? headers :retry-after)
      (let [t (* 1000 (Long/valueOf (:retry-after headers)))]
        (println "rate limit reached, reset at"
                 (new java.util.Date (+ (System/currentTimeMillis) t)))
        (a/<! (a/timeout t))))
    (a/>! throttle :ok)))

(defn build [throttle id out]
  (a/go
    (a/<! throttle)
    (http/get (join "/" [api-root "builds" id])
              {:user-agent api-user-agent
               :headers api-headers}
              (fn [{:keys [status headers body error]}]
                (a/go
                  (a/>! out
                        (cond error
                              (RuntimeException.
                               (str "fetching build " id " failed")
                               error)
                              (= 200 status)
                              (:build (util/parse-json body))
                              :else
                              (RuntimeException.
                               (str "fetching build " id " failed: " body))))
                  (a/close! out)
                  (throttle-if-needed throttle headers)))))
  out)

(defn- min-build-number [builds]
  (->> builds
       (map #(Long/valueOf (:number %)))
       (apply min)))

(defn builds
  ([throttle owner repository out]
   (builds throttle owner repository Long/MAX_VALUE false out))
  ([throttle owner repository after paginate? out]
   (a/go
     (a/<! throttle)
     (http/get (join "/" [api-root "repos" owner repository "builds"])
               {:user-agent api-user-agent
                :headers api-headers
                :query-params {"event_type" "pull_request"
                               "after_number" (str after)}}
               (fn [{:keys [status headers body error]}]
                 (a/go
                   (if (= 200 status)
                     (let [bs (:builds (util/parse-json body))]
                       (a/onto-chan out bs false)
                       (if (or paginate? (empty? bs))
                         (do (a/close! out)
                             (a/>! throttle :ok))
                         (builds throttle
                                 owner
                                 repository
                                 (min-build-number bs)
                                 paginate?
                                 out)))
                     (do
                       (a/>! out
                             (if error
                               (RuntimeException.
                                (str "fetching builds for "
                                     owner "/" repository "failed")
                                error)
                               (RuntimeException.
                                (str "fetching builds for "
                                     owner "/" repository
                                     "failed: " body))))
                       (a/close! out)
                       (throttle-if-needed throttle headers)))))))
   out))

(defn- parse-log-response [headers body]
  (if (.contains (:content-type headers)
                 "text/plain")
    body
    (get-in (util/parse-json body)
            [:log :body])))

(defn log [throttle job-id out]
  (a/go
    (a/<! throttle)
    (http/get (join "/" [api-root "jobs" job-id "log"])
              {:user-agent api-user-agent
               :headers api-headers}
              (fn [{:keys [status headers body error]}]
                (a/go
                  (a/>! out
                        (cond error
                              (RuntimeException.
                               (str "fetching log for job " job-id " failed")
                               error)
                              (= 200 status)
                              (parse-log-response headers body)
                              :else
                              (RuntimeException.
                               (str "fetching log for job " job-id
                                    " failed: " body))))
                  (a/close! out)
                  (throttle-if-needed throttle headers)))))
  out)

(defn notification-build [travis request out]
  (a/go
    (try
      (let [n (util/parse-json (:payload (:params request)))]
        (a/>! out {:build (util/<? (build travis (:id n) (a/chan 1)))
                   :owner (:owner-name (:repository n))
                   :name (:name (:repository n))}))
      (catch Exception e
        (a/>! out e)))
    (a/close! out))
  out)
