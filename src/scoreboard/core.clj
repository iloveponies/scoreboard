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
  (:require [scoreboard.board :as board]
            [scoreboard.score :as score]
            [scoreboard.problem :as problem]
            [scoreboard.in-memory-store :as store]
            [scoreboard.github :as github]
            [scoreboard.travis :as travis]))

(defn parse-log [log]
  (for [score (try (some-> (second (.split log "midje-grader:data"))
                           (json/read-str :key-fn keyword))
                   (catch Exception e))]
    {:exercise (str (:exercise score))
     :points (get score :points (:got score))}))

(defn persist-scores [store chapter author scores]
  (dosync
   (when-let [board (some->> (board/get! store "iloveponies" nil)
                             :key
                             (board/get! store chapter)
                             :key)]
     (doseq [{:keys [exercise points]} scores]
       (some->> (problem/get! store exercise board)
                (score/->Score author points (System/currentTimeMillis))
                (score/insert! store))))))

(defn handle-build [store repo build]
  (let [chapter (:name repo)
        author (github/pull-request-author "iloveponies"
                                           chapter
                                           (:pull-request-number build))
        scores (mapcat parse-log (travis/build-logs build))]
    (persist-scores store chapter author scores)))

(defn handle-repository [store owner name]
  (let [repository (travis/repository owner name)]
    (doseq [build (travis/repository-builds repository)
            :when (:pull-request build)]
      (handle-build store repository build))))

(defn handle-notification [store request]
  (let [{:keys [build repository]} (travis/notification-build request)]
    (when (:pull-request build)
      (handle-build store repository build))))

(def scoreboard (store/->InMemoryStore))

(def notif (atom ""))

(defroutes routes
  (GET "/scoreboard" []
       (let [scores (dosync (board/total! scoreboard "iloveponies" nil))]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/scoreboard/:repo" [repo]
       (let [scores (dosync
                     (let [ilp (board/get! scoreboard "iloveponies" nil)]
                       (board/total! scoreboard repo (:key ilp))))]
         (-> (r/response (json/write-str scores))
             (r/content-type "application/json"))))
  (GET "/notifications" []
       (r/response @notif))
  (POST "/notifications" request
        (do (swap! notif (constantly (:payload (:params request))))
            (handle-notification scoreboard request)
            (r/response "ok")))
  (route/not-found "not found"))

(def chapters
  (map (fn [[chapter [exercise max-points]]]
         [chapter (map vector (map str exercise) max-points)])
       {"training-day"
        [(range 5 8) (repeat 1)]
        "i-am-a-horse-in-the-land-of-booleans"
        [(range 1 9) (repeat 1)]
        "structured-data"
        [(range 1 35) (repeat 1)]
        "p-p-p-pokerface"
        [(range 1 12) (repeat 1)]
        "predicates"
        [(range 1 12) (repeat 1)]
        "recursion"
        [(cons 1 (range 3 30)) (concat (repeat 25 1) [2 3 3])]
        "looping-is-recursion"
        [(range 1 9) (repeat 1)]
        "one-function-to-rule-them-all"
        [(range 1 14) (concat (repeat 12 1) [3])]
        "sudoku"
        [(range 1 16) (repeat 1)]}))

(defn init-store [store]
  (dosync
   (doseq [:let [ilp (board/insert! store (board/->Board "iloveponies" nil))]
           [chapter exercises] chapters
           :let [c (board/insert! store (board/->Board chapter ilp))]
           [e max-points] exercises]
     (problem/insert! store (problem/->Problem e max-points c)))))

(defn -main [port]
  (let [handler (-> routes
                    wrap-keyword-params
                    wrap-params
                    (wrap-cors :access-control-allow-origin #".*"))]
    (init-store scoreboard)
    (server/run-jetty handler {:port (Integer. port) :join? false})
    (doseq [[chapter] chapters]
      (println "preheating author cache," chapter)
      (github/preheat-cache "iloveponies" chapter)
      (println "populating" chapter)
      (handle-repository scoreboard "iloveponies" chapter))
    (println "scoreboard populated")
    (github/clear-cache)))
