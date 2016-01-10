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
            [cheshire.core :as json])
  (:require [scoreboard.scoreboard :as scoreboard]
            [scoreboard.github :as github]
            [scoreboard.travis :as travis]
            [scoreboard.util :as util])
  (:gen-class))

(defn parse-scores [^String log]
  (if-let [data (second (.split log "midje-grader:data"))]
    (for [score (json/parse-string data keyword)]
      (clojure.set/rename-keys score {:got :points
                                      :out-of :max-points}))
    (throw (RuntimeException. "no score data found in log"))))

(defn handle-build [github travis owner name build]
  (let [number (:pull-request-number build)
        author (util/try-times 3 (fn [] (github/pull-request-author github
                                                                    owner
                                                                    name
                                                                    number)))]
    (for [job-id (:job-ids build)
          :let [log (util/try-times 3 (fn [] (travis/log travis job-id)))]
          score (try
                  (parse-scores log)
                  (catch Exception e
                    (println (:id build) (.getMessage e))))]
      (scoreboard/->score
       :user author
       :repo name
       :exercise (:exercise score)
       :points (:points score)
       :max-points (:max-points score)))))

(defn handle-repository [github travis owner name]
  (println (str owner "/" name))
  (let [builds
        (util/collect
         concat []
         (fn [] (util/try-times 3 (fn [] (travis/builds travis owner name)))))]
    (for [build builds
          score (handle-build github travis owner name build)]
      score)))

(defn handle-notification [github travis scoreboard request]
  (let [{:keys [build owner name]}
        (util/try-times 3 (fn [] (travis/notification-build travis request)))]
    (when (:pull-request build)
      (doseq [score (handle-build github travis owner name build)]
        (send scoreboard scoreboard/add-score score)))))

(def scoreboard (agent (scoreboard/->scoreboard)))
(def github (github/->github
             (Integer/valueOf (or (System/getenv "GITHUB_CONCURRENCY") "1"))))
(def travis (travis/->travis
             (Integer/valueOf (or (System/getenv "TRAVIS_CONCURRENCY") "1"))))

(def notif (atom ""))

(defroutes routes
  (GET "/scoreboard" []
       (let [scores (scoreboard/total-scores @scoreboard)]
         (-> (r/response (json/generate-string scores))
             (r/content-type "application/json"))))
  (GET "/scoreboard/:repo" [repo]
       (let [scores (scoreboard/total-scores-by-repo @scoreboard repo)]
         (-> (r/response (json/generate-string scores))
             (r/content-type "application/json"))))
  (GET "/users/:user" [user]
       (let [scores (scoreboard/user-scores @scoreboard user)]
         (-> (r/response (json/generate-string scores))
             (r/content-type "application/json"))))
  (GET "/notifications" []
       (r/response @notif))
  (POST "/notifications" request
        (do (swap! notif (constantly (:payload (:params request))))
            (handle-notification github travis scoreboard request)
            (r/response "ok")))
  (route/not-found "not found"))

(def chapters ["training-day"
               "i-am-a-horse-in-the-land-of-booleans"
               "structured-data"
               "p-p-p-pokerface"
               "predicates"
               "recursion"
               "looping-is-recursion"
               "one-function-to-rule-them-all"
               "sudoku"])

(defn -main [port]
  (let [handler (-> routes
                    wrap-keyword-params
                    wrap-params
                    (wrap-cors :access-control-allow-origin #".*"))]
    (server/run-jetty handler {:port (Integer. port) :join? false})
    (doseq [chapter chapters
            score (handle-repository github travis "iloveponies" chapter)]
      (send scoreboard scoreboard/add-score score))
    (println "scoreboard populated")))
