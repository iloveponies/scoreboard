(ns scoreboard.core
  (:use [compojure.core :only [defroutes GET POST]])
  (:require [ring.adapter.jetty :as server]
            [ring.middleware.reload :as reload]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.util.response :as r]
            [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.data.json :as json])
  (:require [scoreboard.scoreboard :as scoreboard]
            [scoreboard.github :as github]
            [scoreboard.travis :as travis]))

(defn grading-data [job]
  (if-let [log (travis/log-body (travis/job-log! job))]
    (if-let [data (second (.split log "midje-grader:data"))]
      (json/read-str data))
    (println "log missing from job" (travis/job-id job))))

(defn job-to-scoreboard [scoreboard repo job author]
  (reduce (fn [scoreboard points]
            (scoreboard/add-score
             scoreboard
             (scoreboard/->score
              :user author
              :repo repo
              :exercise (get points "exercise")
              :points (get points "got")
              :max-points (get points "out-of"))))
          scoreboard
          (grading-data job)))

(defn update-scoreboard! [scoreboard request]
  (let [build (travis/parse-notification (:payload (:params request)))
        repo (travis/build-repo! build)
        owner (travis/repo-owner repo)
        name (travis/repo-name repo)
        number (travis/build-pull-request-number build)
        author (github/pull-request-author! owner name number)]
    (doseq [job (travis/build-jobs! build)]
      (send scoreboard job-to-scoreboard name job author))))

(defn repo-to-scoreboard! [scoreboard owner name]
  (let [repo (travis/repo! owner name)
        owner (travis/repo-owner repo)
        name (travis/repo-name repo)]
    (doseq [build (travis/repo-builds! repo)
            :let [number (travis/build-pull-request-number build)
                  author (github/pull-request-author! owner name number)]
            job (travis/build-jobs! build)]
      (send scoreboard job-to-scoreboard name job author))))

(def scoreboard (agent (scoreboard/->scoreboard)))

(def notif (atom ""))

(defroutes routes
  (GET "/scoreboard" []
       (let [scores (scoreboard/total-scores @scoreboard)]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/scoreboard/:repo" [repo]
       (let [scores (scoreboard/total-scores-by-repo @scoreboard repo)]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/users/:user" [user]
       (let [scores (scoreboard/user-scores @scoreboard user)]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/notifications" []
       (r/response (str (:payload (:params @notif)))))
  (POST "/notifications" request
        (do (update-scoreboard! scoreboard request)
            (swap! notif (constantly request))
            (r/response "ok")))
  (route/not-found "not found"))

(defn -main [port]
  (let [handler (-> routes
                    wrap-keyword-params
                    wrap-params
                    (wrap-cors :access-control-allow-origin #".*"))]
    (server/run-jetty handler {:port (Integer. port) :join? false})
    (doseq [repo ["training-day"
                  "i-am-a-horse-in-the-land-of-booleans"
                  "structured-data"
                  "p-p-p-pokerface"
                  "predicates"
                  "recursion"
                  "looping-is-recursion"
                  "one-function-to-rule-them-all"
                  "sudoku"]]
      (println "populating" repo)
      (repo-to-scoreboard! scoreboard "iloveponies" repo)
      (println "done"))))
