(ns scoreboard.travis
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def raw-call!
  (rate/rate-limit
   (fn [method url parameters]
     (util/try-times 5
                     (fn [] (method url {:query-params parameters
                                         :headers {"Accept" "application/json; version=2"}}))))
   5000 (* 1000 60 60)))

(defn travis-api! [parameters & url-fragments]
  (let [url (apply str "https://api.travis-ci.org/"
                   (interpose "/" url-fragments))]
    (json/read-str (:body (raw-call! http/get
                                     url
                                     parameters)))))

(defn repo!
  ([repo-id]
     (get (travis-api! {} "repos" repo-id) "repo"))
  ([owner repo]
     (get (travis-api! {} "repos" owner repo) "repo")))

(defn job! [job-id]
  (get (travis-api! {} "jobs" job-id) "job"))

(defn job-id [job]
  (get job "id"))

(defn repo-id [repo]
  (get repo "id"))

(defn repo-owner [repo]
  (first (.split (get repo "slug") "/")))

(defn repo-name [repo]
  (second (.split (get repo "slug") "/")))

(defn build-repo! [build]
  (repo! (get build "repository_id")))

(defn build-number [build]
  (Long/valueOf (get build "number")))

(defn build-pull-request-number [build]
  (get build "pull_request_number"))

(defn build-jobs! [build]
  (map job! (get build "job_ids")))

(defn job-log! [job]
  (get (travis-api! {} "logs" (get job "log_id")) "log"))

(defn log-body [log]
  (let [b (get log "body")]
    (when (not (empty? b))
      b)))

(defn repo-builds!
  ([repo]
     (loop [builds []
            smallest-number Long/MAX_VALUE]
       (let [chunk (repo-builds! repo smallest-number)]
         (if (empty? chunk)
           builds
           (recur (concat builds chunk)
                  (apply min (map build-number chunk)))))))
  ([repo after]
     (filter #(get % "pull_request")
             (get (travis-api! {"repository_id" (repo-id repo)
                                "after_number" after}
                               "builds")
                  "builds"))))

(defn parse-notification [payload-str]
  (json/read-str payload-str))
