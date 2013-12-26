(ns scoreboard.github
  (:require [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def ^{:dynamic true} *user* (System/getenv "GITHUB_USER"))
(def ^{:dynamic true} *passwd* (System/getenv "GITHUB_PASSWD"))

(def http
  (rate/rate-limit
   (fn [method url parameters]
     (let [p {:query-params parameters
              :basic-auth [*user* *passwd*]}]
       (util/retrying-http method url p [1 2 8])))
   5000 (* 1000 60 60)))

(defn api [parameters & url-fragments]
  (let [url (apply str "https://api.github.com/"
                   (interpose "/" url-fragments))]
    (http :get url parameters)))

(defn json-api [parameters & url-fragments]
  (json/read-str (:body (apply api parameters url-fragments))
                 :key-fn keyword))

(defn pull-request [owner repo number]
  (json-api {} "repos" owner repo "pulls" number))

(defn pull-requests
  ([owner repo]
     (concat (pull-requests owner repo "open")
             (pull-requests owner repo "closed")))
  ([owner repo state]
     (let [p {"state" state
              "per_page" 100}]
       (loop [{:keys [links body]} (api p "repos" owner repo "pulls")
              prs []]
         (let [prs (concat prs (json/read-str body :key-fn keyword))]
           (if (contains? links :next)
             (recur (http :get (:href (:next links)) p)
                    prs)
             prs))))))

(defn pr-author [pull-request]
  (get-in pull-request [:head :repo :owner :login]))

(defn update-cache [cache pull-request]
  (let [owner (get-in pull-request [:base :repo :owner :login])
        name (get-in pull-request [:base :repo :name])
        number (:number pull-request)
        author (pr-author pull-request)]
    (assoc cache [owner name number] author)))

(let [cache (atom {})]
  (defn preheat-cache [owner repo]
    (doseq [pr (pull-requests owner repo)]
      (swap! cache update-cache pr)))

  (defn pull-request-author [owner repo number]
    (if-let [author (get @cache [owner repo number])]
      author
      (let [pr (pull-request owner repo number)]
        (swap! cache update-cache pr)
        (pr-author pr)))))
