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
            [scoreboard.travis :as travis]
            [scoreboard.util :as util])
  (:gen-class))

(defn parse-scores [^String log]
  (if-let [data (second (.split log "midje-grader:data"))]
    (for [score (json/parse-string data keyword)]
      (clojure.set/rename-keys score {:got :points
                                      :out-of :max-points}))
    (throw (RuntimeException. "no score data found in log"))))

(defn- fetch-author [github owner repository build]
  (let [number (:pull-request-number build)]
    (util/try-times 3
                    #(github/pull-request-author github
                                                 owner
                                                 repository
                                                 number
                                                 %)
                    (a/chan 1))))

(defn- fetch-logs [travis build]
  (let [job-count (count (:job-ids build))
        logs (a/chan job-count)]
    (a/pipeline-async job-count
                      logs
                      (fn [job-id out]
                        (util/try-times 3
                                        #(travis/log travis job-id %)
                                        out))
                      (a/to-chan (:job-ids build)))
    logs))

(defn- error-messages [e]
  (loop [msg (.getMessage e)
         cause (.getCause e)]
    (if cause
      (recur (str msg "\n" (.getMessage cause))
             (.getCause cause))
      msg)))

(defn handle-build [github travis owner name build out]
  (a/go
    (try
      (let [author (util/<? (fetch-author github owner name build))
            logs (fetch-logs travis build)]
        (loop [log (util/<? logs)]
          (when log
            (doseq [score (parse-scores log)]
              (a/>! out (scoreboard/->score
                         :user author
                         :repo name
                         :exercise (:exercise score)
                         :points (:points score)
                         :max-points (:max-points score))))
            (recur (a/<! logs)))))
      (catch Exception e
        (println "error while handling build" (:id build) (error-messages e))))
    (a/close! out)))

(defn handle-repository [scoreboard github travis owner name]
  (a/go
    (println (str "handling repository " owner "/" name))
    (let [scores (a/chan)]
      (a/pipeline-async 1
                        scores
                        (partial handle-build github travis owner name)
                        (travis/builds travis owner name (a/chan)))
      (loop [score (a/<! scores)]
        (when score
          (send scoreboard scoreboard/add-score score)
          (recur (a/<! scores)))))
    (println (str owner "/" name " handled"))))

(defn handle-notification [scoreboard github travis request]
  (a/go
    (let [{:keys [build owner name]}
          (util/<? (travis/notification-build travis request (a/chan 1)))]
      (when (:pull-request build)
        (handle-build scoreboard github travis owner name build)))))

(def scoreboard (agent (scoreboard/->scoreboard)))
(def github (github/->github 4))
(def travis (travis/->travis 4))

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
            (handle-notification scoreboard github travis request)
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
      (handle-repository scoreboard github travis "iloveponies" chapter))
    (println "scoreboard populated")))
