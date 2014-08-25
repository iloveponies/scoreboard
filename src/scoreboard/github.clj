(ns scoreboard.github
  (:require [tentacles.pulls :as t]))

(def ^{:dynamic true} *auth* (System/getenv "GITHUB_AUTH"))

(let [cache (atom {})]
  (defn init-cache [owner repo]
    (doseq [pr (concat (t/pulls owner repo {:state "open"
                                            :all-pages true
                                            :auth *auth*})
                       (t/pulls owner repo {:state "closed"
                                            :all-pages true
                                            :auth *auth*}))]
      (when-let [login (get-in pr [:user :login])]
        (swap! cache assoc [owner repo (:number pr)] login))))

  (defn clear-cache []
    (reset! cache {}))

  (defn pull-request-author [owner repo number]
    (if-let [author (get @cache [owner repo number])]
      author
      (get-in (t/specific-pull owner repo number {:auth *auth*})
              [:user :login]))))
