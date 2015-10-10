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
            [clojure.core.async :as a]
            [cheshire.core :as json])
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
    (for [score (json/parse-string data keyword)]
      (clojure.set/rename-keys score {:got :points
                                      :out-of :max-points}))
    (on-error)))

(defn handle-build [scoreboard github travis owner name build]
  (let [number (:pull-request-number build)
        author (let [c (a/chan 1)]
                 (a/>!! github (github/pull-request-author owner name number c))
                 (:ok (a/<!! c)))]
    (doseq [log (map #(let [log (a/chan 1)]
                        (a/>!! travis (travis/log % log))
                        (:ok (a/<!! log)))
                     (:job-ids build))
            score (parse-scores log #(println "data missing from build"
                                              (:id build)))]
      (println author score)
      (send scoreboard score-to-scoreboard name author score))))

(defn handle-repository [scoreboard github travis owner name]
  (let [builds (a/chan)]
    (a/>!! travis (travis/builds owner name builds))
    (loop [build (a/<!! builds)]
      (when build
        (handle-build scoreboard github travis owner name (:ok build))
        (recur (a/<!! builds))))))

(defn handle-notification [scoreboard request]
  (let [{:keys [build owner name]} (travis/notification-build request)]
    (when (:pull-request build)
      (handle-build scoreboard owner name build))))

(def scoreboard (agent (scoreboard/->scoreboard)))

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
                    (wrap-cors :access-control-allow-origin #".*"))
        github (github/->github 1)
        travis (travis/->travis 2)]
    (server/run-jetty handler {:port (Integer. port) :join? false})
    (doseq [chapter chapters]
      (println "populating" chapter)
      (handle-repository scoreboard github travis "iloveponies" chapter))
    (println "scoreboard populated")))
