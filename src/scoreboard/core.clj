(ns scoreboard.core
  (:use [compojure.core :only [defroutes GET POST]])
  (:require [org.httpkit.server :as server]
            [org.httpkit.client :as http]
            [ring.middleware.reload :as reload]
            [ring.util.response :as r]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json])
  (:require [scoreboard.scores :as scores]))

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

(defn sum-points [grading-data]
  (reduce (fn [acc e]
            (-> acc
                (update-in [:got] + (get e "got"))
                (update-in [:out-of] + (get e "out-of"))))
          {:got 0 :out-of 0}
          grading-data))

(defn max-points [jobs]
  (apply merge-with max (map (comp sum-points grading-data) jobs)))

(defn points-per-author [jobs repo]
  (let [authors (set (map job-author jobs))
        scores (scores/->in-memory-scores)]
    (doseq [author authors
            job (jobs-by-author author jobs)
            points (grading-data job)]
      (scores/update-score! scores author repo (get points "exercise")
                            {:got (get points "got")
                             :out-of (get points "out-of")}))
    (map (fn [author]
           {author (scores/get-scores scores author)})
         authors)))

(defn scoreboard [owner repo]
  (points-per-author (mapcat jobs (builds owner repo)) repo))

(defn job-to-scoreboard! [scoreboard repo job]
  (doseq [points (grading-data job)]
    (scores/update-score! scoreboard
                          (job-author job)
                          repo
                          (get points "exercise")
                          {:got (get points "got")
                           :out-of (get points "out-of")})))

(defn repo-to-scoreboard! [scoreboard owner repo]
  (doseq [build (builds owner repo)
          job (jobs build)]
    (job-to-scoreboard! scoreboard repo job)))

(defn populate-scoreboard! [scoreboard owner]
  (doseq [repo (repositories owner)]
    (repo-to-scoreboard! scoreboard owner repo)
    (println "populated " repo)))

(defn update-scoreboard! [scoreboard request]
  (let [build (json/read-str (:payload (:params request)))
        repo (get-in build ["repository" "name"])]
    (doseq [job (jobs build)]
      (job-to-scoreboard! scoreboard repo job))))

(def scoreboard (scores/->in-memory-scores))
(def notif (atom nil))

(defroutes routes
  (GET "/scoreboard" []
       (let [scores (scores/get-scores scoreboard)]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/scoreboard/:repo" [repo]
       (let [scores (scores/get-scores scoreboard repo)]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (POST "/notifications" request
        (do (update-scoreboard! scoreboard request)
            (r/response "ok")))
  (route/not-found "not found"))

(defn -main [port]
  (let [handler (reload/wrap-reload (handler/site #'routes))]
    (server/run-server handler {:port (Integer. port)})
    (println "populating scoreboard")
    ;;(populate-scoreboard! scoreboard "iloveponies")
    ;;(repo-to-scoreboard! scoreboard "iloveponies" "structured-data")
    (println "scoreboard populated")))
