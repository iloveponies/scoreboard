(defproject scoreboard "0.1.0-SNAPSHOT"
  :description "scoreboard"
  :url "http://github.com/iloveponies/scoreboard"
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [org.clojure/core.async "0.2.374"]
                 [cheshire "5.5.0"]
                 [http-kit "2.1.18"]
                 [ring/ring-core "1.2.1"]
                 [ring/ring-devel "1.2.1"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring-cors "0.1.0"]
                 [compojure "1.1.5"]]
  :profiles {:dev {:dependencies [[midje "1.5.1"]]
                   :plugins [[lein-midje "3.1.1"]]}}
  :uberjar-name "scoreboard-standalone.jar"
  :min-lein-version "2.0.0"
  :main scoreboard.core
  :aot [scoreboard.core])
