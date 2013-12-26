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

(defn score-to-scoreboard [scoreboard repo author score]
  (scoreboard/add-score scoreboard
                        (scoreboard/->score
                         :user author
                         :repo repo
                         :exercise (:exercise score)
                         :points (:points score)
                         :max-points (:max-points score))))

(defn parse-scores [^String log on-error]
  (if-let [data (second (.split log "midje-grader:data"))]
    (for [score (json/read-str data :key-fn keyword)]
      (clojure.set/rename-keys score {:got :points
                                      :out-of :max-points}))
    (on-error)))

(defn handle-build [scoreboard repo build]
  (let [owner (:owner repo)
        name (:name repo)
        number (:pull-request-number build)
        author (github/pull-request-author owner name number)]
    (doseq [log (travis/build-logs build)
            score (parse-scores log #(println "data missing from build"
                                              (:id build)))]
      (send scoreboard score-to-scoreboard name author score))))

(defn handle-repository [scoreboard owner name]
  (let [repository (travis/repository owner name)]
    (doseq [build (travis/repository-builds repository)
            :when (:pull-request build)]
      (handle-build scoreboard repository build))))

(defn handle-notification [scoreboard request]
  (let [{:keys [build repository]} (travis/notification-build request)]
    (when (:pull-request build)
      (handle-build scoreboard repository build))))

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
       (r/response @notif))
  (POST "/notifications" request
        (do (swap! notif (constantly (:payload (:params request))))
            (handle-notification scoreboard request)
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
    (doseq [chapter chapters]
      (println "preheating author cache," chapter)
      (github/preheat-cache "iloveponies" chapter))
    (doseq [chapter chapters]
      (println "populating" chapter)
      (handle-repository scoreboard "iloveponies" chapter))
    (println "scoreboard populated")))
