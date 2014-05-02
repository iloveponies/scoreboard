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

(defn parse-json [s]
  (json/read-str s :key-fn #(keyword (.replace % "_" "-"))))

(defn json-api [parameters & url-fragments]
  (parse-json (:body (apply api parameters url-fragments))))

(defn parse-build [build]
  (let [pr (fn [build]
             (if (not (contains? build :pull-request))
               (assoc build :pull-request (= (:type build) "pull_request"))
               build))
        pr-number (fn [build]
                    (if (not (contains? build :pull-request-number))
                      (assoc build
                        :pull-request-number (-> (:compare-url build)
                                                 (.split "/")
                                                 last
                                                 Long/valueOf))
                      build))
        job-ids (fn [build]
                  (if (not (contains? build :job-ids))
                    (assoc build :job-ids (map :id (:matrix build)))
                    build))]
    (-> build
        pr
        pr-number
        job-ids
        (update-in [:number] #(Long/valueOf %)))))

(defn build [id]
  (parse-build (:build (json-api {} "builds" id))))

(defn parse-repository [repository]
  (let [[owner name] (if (contains? repository :slug)
                       (.split (:slug repository) "/")
                       [(:owner-name repository) (:name repository)])]
    (-> repository
        (dissoc :slug)
        (dissoc :owner-name)
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
  (let [build (parse-build (parse-json (:payload (:params request))))
        repository (parse-repository (:repository build))]
    {:build build
     :repository repository}))
