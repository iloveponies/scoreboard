(defproject scoreboard "0.1.0-SNAPSHOT"
  :description "scoreboard"
  :url "http://github.com/iloveponies/scoreboard"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [tentacles "0.2.5"]
                 [cheshire "5.3.1"]
                 [rate-gate "1.3.1"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring-cors "0.1.0"]
                 [clj-http "0.7.7"]
                 [compojure "1.1.5"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}}
  :uberjar-name "scoreboard-standalone.jar"
  :min-lein-version "2.0.0"
  :main scoreboard.core
  :aot [scoreboard.core])
