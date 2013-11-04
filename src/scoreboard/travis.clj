(ns scoreboard.travis
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.github :as github]
            [scoreboard.util :as util]))

(def raw-call!
  (rate/rate-limit
   (fn [method url parameters]
     (util/try-times 5 (fn [] (method url {:query-params parameters}))))
   5000 (* 1000 60 60)))

(defn travis-api! [parameters & url-fragments]
  (let [url (apply str "https://api.travis-ci.org/"
                   (interpose "/" url-fragments))]
    (json/read-str (:body (raw-call! http/get
                                     url
                                     parameters)))))

(defn build! [build-id]
  (travis-api! {} "builds" build-id))

(defn builds! [owner repo]
  (let [repo-id (get (travis-api! {} "repos" owner repo) "id")]
    (loop [builds []
           build-chunk (travis-api! {"repository_id" repo-id
                                     "event_type" "pull_request"}
                                    "builds")]
      (let [build-numbers (map #(Integer. (get % "number")) build-chunk)
            min-number (if (empty? build-numbers) 1 (apply min build-numbers))]
        (if (= min-number 1)
          (map #(build! (get % "id")) (concat builds build-chunk))
          (recur (concat builds build-chunk)
                 (travis-api! {"repository_id" repo-id
                               "event_type" "pull_request"
                               "after_number" min-number}
                              "builds")))))))

(defn job! [job-id]
  (travis-api! {} "jobs" job-id))

(defn build-jobs! [build]
  (let [jobs (get build "jobs"
                  (get build "matrix"))]
    (map (comp job! #(get % "id")) jobs)))

(defn build-author! [build]
  (let [url (get-in build ["commit" "compare_url"]
                    (get build "compare_url"))
        url (.split url "/")
        owner (get url 3)
        repo (get url 4)
        number (Integer. (get url 6))]
    (github/pull-request-author! owner repo number)))
