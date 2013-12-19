(ns scoreboard.github
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [rate-gate.core :as rate]
            [scoreboard.util :as util]))

(def ^{:dynamic true} *user* (System/getenv "GITHUB_USER"))
(def ^{:dynamic true} *passwd* (System/getenv "GITHUB_PASSWD"))

(def raw-call!
  (rate/rate-limit
   (fn [method url parameters]
     (util/try-times 5 #(method url {:query-params parameters
                                     :basic-auth [*user* *passwd*]})))
   5000 (* 1000 60 60)))

(defn api! [parameters & url-fragments]
  (let [url (apply str "https://api.github.com/"
                   (interpose "/" url-fragments))]
    (raw-call! http/get url parameters)))

(defn pull-requests!
  ([owner repo]
     (concat (pull-requests! owner repo "open")
             (pull-requests! owner repo "closed")))
  ([owner repo state]
     (loop [{:keys [links body]}
            (api! {"state" state} "repos" owner repo "pulls")
            prs []]
       (let [prs (concat prs (json/read-str body :key-fn keyword))]
         (if (contains? links :next)
           (recur (raw-call! http/get (:href (:next links)) {"state" state})
                  prs)
           prs)))))

(defn pull-request-number [pull-request]
  (:number pull-request))

(defn pull-request-author [pull-request]
  (get-in pull-request [:user :login]))

(defn update-author-cache! [cache owner repo]
  (doseq [pull-request (pull-requests! owner repo)
          :let [n (pull-request-number pull-request)
                author (pull-request-author pull-request)]]
    (swap! cache assoc [owner repo n] author)))

(let [cache (atom {})]
  (defn pull-request-author [owner repo number]
    (if-let [author (get @cache [owner repo number])]
      author
      (do (update-author-cache! cache owner repo)
          (get @cache [owner repo number])))))
