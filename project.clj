(defproject scoreboard "0.1.0-SNAPSHOT"
  :description "scoreboard"
  :url "http://github.com/iloveponies/scoreboard"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/data.json "0.2.3"]
                 [ring/ring-core "1.2.0"]
                 [ring/ring-devel "1.2.0"]
                 [ring-cors "0.1.0"]
                 [http-kit "2.1.12"]
                 [compojure "1.1.5"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}}
  :min-lein-version "2.0.0"
  :main scoreboard.core)
