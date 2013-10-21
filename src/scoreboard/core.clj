(ns scoreboard.core
  (:use [compojure.core :only [defroutes GET]])
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [ring.middleware.reload :as reload]
            [ring.util.response :as r]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json]))

(def travis-api "https://api.travis-ci.org")

(def build
  (memoize
   (fn [build-id]
     (json/read-str (:body @(http/get (str travis-api
                                           "/builds/" build-id)))))))

(defn builds [owner repo]
  (map (comp build #(get % "id"))
       (json/read-str
        (:body @(http/get (str travis-api
                               "/repos/" owner "/" repo "/builds"))))))

(def job
  (memoize
   (fn [job-id]
     (json/read-str (:body @(http/get (str travis-api
                                           "/jobs/" job-id)))))))

(defn jobs [build]
  (map (comp job #(get % "id"))
       (get build "matrix")))

(defn job-author [job]
  (get job "author_name"))

(defn jobs-by-author [author jobs]
  (filter #(= author (job-author %)) jobs))

(defn grading-data [job]
  (if-let [data (second (.split (get job "log") "midje-grader:data"))]
    (json/read-str data)))

(defn sum-points [grading-data]
  (reduce (fn [acc e]
            (-> acc
                (update-in [:got] + (get e "got"))
                (update-in [:out-of] + (get e "out-of"))))
          {:got 0 :out-of 0}
          grading-data))

(defn max-points [jobs]
  (apply merge-with max (map (comp sum-points grading-data) jobs)))

(defn points-per-author [jobs]
  (let [authors (set (map job-author jobs))]
    (map (fn [a]
           {:author a
            :points (max-points (jobs-by-author a jobs))})
         authors)))

(defn scoreboard [owner repo]
  (points-per-author (mapcat jobs (builds owner repo))))

(defroutes routes
  (GET "/scoreboard/:owner/:repo" [owner repo]
       (-> (r/response (json/write-str (scoreboard owner repo)))
           (r/content-type "application/json")))
  (route/not-found "not found"))

(defn -main [& args]
  (let [handler (reload/wrap-reload (handler/site #'routes))]
    (server/run-server handler {:port 8080})))
