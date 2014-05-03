(ns scoreboard.github
  (:require [tentacles.pulls :as tentacles]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def ^{:dynamic true} *user* (System/getenv "GITHUB_USER"))
(def ^{:dynamic true} *passwd* (System/getenv "GITHUB_PASSWD"))

(def specific-pull
  (-> (fn [user repo id & [options]]
        (tentacles/specific-pull user repo id
                                 (merge options
                                        {:auth (str *user* ":" *passwd*)})))
      (rate/rate-limit 5000 (* 1000 60 60))
      (util/retrying [500 1000 2000])))

(def pulls
  (-> (fn [user repo & [options]]
        (tentacles/pulls user repo (merge options
                                          {:auth (str *user* ":" *passwd*)})))
      (rate/rate-limit 5000 (* 1000 60 60))
      (util/retrying [500 1000 2000])))

(defn update-cache [cache pull-request]
  (let [owner (get-in pull-request [:base :repo :owner :login])
        name (get-in pull-request [:base :repo :name])
        number (:number pull-request)
        author (get-in pull-request [:head :user :login])]
    (assoc cache [owner name number] author)))

(let [cache (atom {})]
  (defn preheat-cache [owner repo]
    (doseq [pr (concat (pulls owner repo {:all-pages true})
                       (pulls owner repo {:state :closed
                                          :all-pages true}))]
      (swap! cache update-cache pr)))

  (defn clear-cache []
    (reset! cache {}))

  (defn pull-request-author [owner repo number]
    (if-let [author (get @cache [owner repo number])]
      author
      (let [pr (specific-pull owner repo number)
            author (get-in pr [:head :user :login])]
        (swap! cache update-cache pr)
        author))))
