(ns scoreboard.travis
  (:require [cheshire.core :as json]
            [scoreboard.util :as util]))

(def http
  (fn [method url parameters]
    (let [p {:query-params parameters
             :headers {"Accept" (str "application/vnd.travis-ci.2+json"
                                     ", application/json"
                                     ", text/plain")
                       "User-Agent" "ILovePonies/1.0.0"}}]
      (util/retrying-http method url p [1 2 8]))))

(defn api [parameters & url-fragments]
  (let [url (apply str "https://api.travis-ci.org/"
                   (interpose "/" url-fragments))]
    (http :get url parameters)))

(defn parse-json [s]
  (json/parse-string s (fn [k] (keyword (.replace k "_" "-")))))

(defn json-api [parameters & url-fragments]
  (parse-json (:body (apply api parameters url-fragments))))

(defn build [id]
  (:build (json-api {} "builds" id)))

(defn builds
  ([owner name]
     (loop [bs []
            chunk (builds owner name Long/MAX_VALUE)]
       (if (empty? chunk)
         bs
         (let [min-number (->> (map :number chunk)
                               (map #(Long/valueOf %))
                               (apply min))]
           (recur (concat bs chunk) (builds owner name min-number))))))
  ([owner name after]
     (:builds (json-api {"event_type" "pull_request"
                         "after_number" (str after)}
                        "repos" owner name "builds"))))

(defn build-logs [build]
  (doall
   (for [job-id (:job-ids build)
         :let [r (api {} "jobs" (str job-id) "log")]]
     (if (.contains (get (:headers r) "content-type") "text/plain")
       (:body r)
       (get-in (parse-json (:body r)) [:log :body])))))

(defn notification-build [request]
  (let [n (parse-json (:payload (:params request)))]
    {:build (build (:id n))
     :owner (:owner-name (:repository n))
     :name (:name (:repository n))}))
