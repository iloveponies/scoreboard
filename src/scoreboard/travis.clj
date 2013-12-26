(ns scoreboard.travis
  (:require [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def http
  (rate/rate-limit
   (fn [method url parameters]
     (let [p {:query-params parameters
              :headers {"Accept" "application/json; version=2"}}]
       (util/retrying-http method url p [1 2 8])))
   5000 (* 1000 60 60)))

(defn api [parameters & url-fragments]
  (let [url (apply str "https://api.travis-ci.org/"
                   (interpose "/" url-fragments))]
    (http :get url parameters)))

(defn json-api [parameters & url-fragments]
  (json/read-str (:body (apply api parameters url-fragments))
                 :key-fn keyword))

(defn parse-build [build]
  (let [pull-request (or (= (:type build) "pull_request")
                         (:pull_request build))
        pull-request-n (if (contains? build :pull_request_number)
                         (:pull_request_number build)
                         (-> (:compare_url build)
                             (.split "/")
                             last
                             Long/valueOf))]
    (-> build
        (update-in [:number] #(Long/valueOf %))
        (update-in [:matrix] #(map :id %))
        (dissoc :type)
        (dissoc :pull_request)
        (assoc :pull-request pull-request)
        (dissoc :pull_request_number)
        (assoc :pull-request-number pull-request-n)
        (clojure.set/rename-keys {:matrix :job-ids
                                  :job_ids :job-ids
                                  :repository_id :repository-id}))))

(defn build [id]
  (parse-build (:build (json-api {} "builds" id))))

(defn parse-repository [repository]
  (let [[owner name] (if (contains? repository :slug)
                       (.split (:slug repository) "/")
                       [(:owner_name repository) (:name repository)])]
    (-> repository
        (dissoc :slug)
        (dissoc :owner_name)
        (assoc :owner owner)
        (assoc :name name))))

(defn repository
  ([id] (parse-repository (:repo (json-api {} "repos" id))))
  ([owner name] (parse-repository (:repo (json-api {} "repos" owner name)))))

(defn repository-builds
  ([repository]
     (loop [builds []
            chunk (repository-builds repository Long/MAX_VALUE)]
       (if (empty? chunk)
         builds
         (recur (concat builds chunk)
                (repository-builds repository
                                   (apply min (map :number chunk)))))))
  ([repository after]
     (map parse-build
          (:builds (json-api {"after_number" after}
                             "repos"
                             (:owner repository)
                             (:name repository)
                             "builds")))))

(defn log [job-id]
  (:body (api {} "jobs" job-id "log")))

(defn build-logs [build]
  (for [job-id (:job-ids build)]
    (log job-id)))

(defn build-repository [build]
  (repository (:repository-id build)))

(defn notification-build [request]
  (let [build (parse-build (json/read-str (:payload (:params request))
                                          :key-fn keyword))
        repository (parse-repository (:repository build))]
    {:build build
     :repository repository}))
