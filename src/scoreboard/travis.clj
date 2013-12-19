(ns scoreboard.travis
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def raw-call!
  (rate/rate-limit
   (fn [method url parameters]
     (util/try-times 5
                     #(method url {:query-params parameters
                                   :headers {"Accept" "application/json; version=2"}})))
   5000 (* 1000 60 60)))

(defn api! [parameters & url-fragments]
  (let [url (apply str "https://api.travis-ci.org/"
                   (interpose "/" url-fragments))]
    (:body (raw-call! http/get url parameters))))

(defn json-api! [parameters & url-fragments]
  (json/read-str (apply api! parameters url-fragments)
                 :key-fn keyword))



(defn parse-build [build]
  (-> build
      (update-in [:number] #(Long/valueOf %))
      (clojure.set/rename-keys {:pull_request :pull-request
                                :pull_request_number :pull-request-number})))

(defn build [id]
  (parse-build (:build (json-api! {} "builds" id))))

(defn parse-repo [repo]
  (let [[owner name] (.split (:slug repo) "/")]
    (-> repo
        (dissoc :slug)
        (assoc :owner owner)
        (assoc :name name)
        (clojure.set/rename-keys
         {:pull_request_number :pull-request-number}))))

(defn repo
  ([id] (parse-repo (:repo (json-api! {} "repos" id))))
  ([owner repo] (parse-repo (:repo (json-api! {} "repos" owner repo)))))

(defn repo-builds
  ([repo]
     (loop [builds []
            chunk (repo-builds repo Long/MAX_VALUE)]
       (if (empty? chunk)
         builds
         (recur (concat builds chunk)
                (repo-builds repo (apply min (map :number chunk)))))))
  ([repo after]
     (map parse-build
          (:builds (json-api! {"repository_id" (:id repo)
                               "after_number" after}
                              "builds")))))

(defn log [job-id]
  (api! {} "jobs" job-id "log"))

(defn build-logs [build]
  (for [job-id (:job_ids build)]
    (log job-id)))

(defn build-repo [build]
  (repo (:repository_id build)))

(defn notification-build [request]
  (build (:id (json/read-str (:payload (:params request)) :key-fn keyword))))
