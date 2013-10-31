(ns scoreboard.github
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [rate-gate.core :as rate]))

(def raw-call!
  (rate/rate-limit
   (fn [method url parameters]
     (let [user (System/getenv "GITHUB_USER")
           passwd (System/getenv "GITHUB_PASSWD")]
       (method url {:query-params parameters
                    :basic-auth [user passwd]})))
   5000 (* 1000 60 60)))

(defn github-api!
  ([parameters & url-fragments]
     (let [url (apply str "https://api.github.com/"
                      (interpose "/" url-fragments))]
       (raw-call! http/get url parameters))))

(defn pull-requests!
  ([owner repo]
     (concat (pull-requests! owner repo "open")
             (pull-requests! owner repo "closed")))
  ([owner repo state]
     (loop [{:keys [links body]}
            (github-api! {"state" state} "repos" owner repo "pulls")
            prs []]
       (if (contains? links :next)
         (recur (raw-call! http/get (:href (:next links)) {"state" state})
                (concat prs (json/read-str body)))
         (concat prs (json/read-str body))))))

(defn pull-request-number [pull-request]
  (get pull-request "number"))

(defn pull-request-author [pull-request]
  (get-in pull-request ["user" "login"]))

(defn update-author-cache! [cache owner repo]
  (let [pull-requests (pull-requests! owner repo)]
    (swap! cache (fn [cache]
                   (reduce (fn [cache pr]
                             (assoc cache
                               [owner repo (pull-request-number pr)]
                               (pull-request-author pr)))
                           cache
                           pull-requests)))))

(let [cache (atom {})]
  (defn pull-request-author! [owner repo number]
    (if (contains? @cache [owner repo number])
      (get @cache [owner repo number])
      (do (update-author-cache! cache owner repo)
          (get @cache [owner repo number])))))
