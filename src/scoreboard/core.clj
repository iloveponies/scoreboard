(ns scoreboard.core
  (:use [compojure.core :only [defroutes GET POST]])
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :as params]
            [ring.util.response :as r]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json])
  (:require [scoreboard.scoreboard :as scoreboard]))

(def travis-api "https://api.travis-ci.org")

(defn repositories [owner]
  (map #(second (.split (get % "slug") "/"))
       (json/read-str (:body @(http/get (str travis-api "/repos/" owner))))))

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

(defn job-to-scoreboard [scoreboard repo job]
  (reduce (fn [scoreboard points]
            (scoreboard/add-score
             scoreboard
             (scoreboard/->score
              :user (job-author job)
              :exercise (str repo "." (get points "exercise"))
              :points (get points "got")
              :max-points (get points "out-of"))))
          scoreboard
          (grading-data job)))

(defn repo-to-scoreboard! [scoreboard owner repo]
  (doseq [build (builds owner repo)
          job (jobs build)]
    (job-to-scoreboard scoreboard repo job)))

(defn populate-scoreboard! [scoreboard owner]
  (doseq [repo (repositories owner)]
    (repo-to-scoreboard! scoreboard owner repo)))

(defn update-scoreboard! [scoreboard request]
  (let [build (json/read-str (:payload (:params request)))
        repo (get-in build ["repository" "name"])]
    (doseq [job (jobs build)]
      (send scoreboard job-to-scoreboard repo job))))

(defn get-scores [scoreboard level total?]
  (if total?
    (scoreboard/total-score-at-level scoreboard level)
    (scoreboard/scores-at-level scoreboard level)))

(def scoreboard (agent (scoreboard/->scoreboard)))

(defroutes routes
  (GET "/scoreboard" [total?]
       (-> (r/response (json/write-str (get-scores @scoreboard "" total?)))
           (r/content-type "application/json")))
  (GET "/scoreboard/*" [* total?]
       (let [level (.replace * \/ \.)]
         (-> (r/response (json/write-str (get-scores @scoreboard level total?)))
             (r/content-type "application/json"))))
  (POST "/notifications" request
        (do (update-scoreboard! scoreboard request)
            (r/response "ok")))
  (route/not-found "not found"))

(defn -main [port]
  (let [handler (-> (handler/site #'routes)
                    reload/wrap-reload
                    params/wrap-params)]
    (server/run-server handler {:port (Integer. port)})
    ;;(println "populating scoreboard")
    ;;(populate-scoreboard! scoreboard "iloveponies")
    ;;(repo-to-scoreboard! scoreboard "iloveponies" "structured-data")
    ;;(println "scoreboard populated")
    ))
