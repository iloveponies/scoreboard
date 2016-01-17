(ns scoreboard.github
  (:require [clj-http.client :as http]
            [clojure.core.match :refer [match]]
            [clojure.string :refer [join split trim]]
            [scoreboard.util :as util]))

(def api-root "https://api.github.com")

(def api-params (if-let [auth (System/getenv "GITHUB_AUTH")]
                  {:throw-exceptions false
                   :basic-auth (split auth #":")}
                  {:throw-exceptions false}))

(defn- rate-limit-reached? [headers]
  (= "0" (:x-ratelimit-remaining headers)))

(defn- rate-limit-reset [headers]
  (* 1000 (Long/valueOf (:x-ratelimit-reset headers))))

(def ->github util/->rate-limited-pool)

(defn- ->ok [body]
  {:ok (util/parse-json body)})

(defn- ->rate-limit-reached [url next-reset]
  {:error (format "Rate limit reached, next reset %s: %s"
                  (java.util.Date. next-reset) url)
   :next-reset next-reset})

(defn- ->error [status url body]
  {:error (format "Error %d, %s: %s"
                  status (:message (util/parse-json body)) url)})

(defn pull-request [github owner repository number]
  (letfn [(c []
            (let [url (join "/" [api-root "repos" owner repository "pulls" number])
                  {:keys [status headers body]} (http/get url api-params)
                  result (cond (= 200 status)
                               (->ok body)
                               (and (= 403 status) (rate-limit-reached? headers))
                               (->rate-limit-reached
                                url
                                (rate-limit-reset headers))
                               :else
                               (->error status url body))]
              (if (rate-limit-reached? headers)
                {:rate-limit-reached? true
                 :next-reset (rate-limit-reset headers)
                 :result result}
                {:rate-limit-reached? false
                 :result result})))]
    (util/submit github c)))

(defn pull-requests
  ([github owner repository]
   (pull-requests github (join "/" [api-root "repos" owner repository "pulls"])))
  ([github url]
   (letfn [(->ok [links body]
             (let [prs (util/parse-json body)]
               (if-let [next-link (:next links)]
                 {:ok {:result prs
                       :next (fn [] (pull-requests github (:href next-link)))}}
                 {:ok {:result prs}})))
           (c []
             (let [{:keys [status links headers body]}
                   (http/get url (assoc api-params
                                   :query-params {"per_page" "100"
                                                  "state" "all"}))
                   result (cond (= 200 status)
                                (->ok links body)
                                (and (= 403 status) (rate-limit-reached? headers))
                                (->rate-limit-reached
                                 url
                                 (rate-limit-reset headers))
                                :else
                                (->error status url body))]
               (if (rate-limit-reached? headers)
                 {:rate-limit-reached? true
                  :next-reset (rate-limit-reset headers)
                  :result result}
                 {:rate-limit-reached? false
                  :result result})))]
     (util/submit github c))))

(defn pull-request-author [github owner repository number]
  (match [(pull-request github owner repository number)]
         [{:ok {:user {:login author}}}] {:ok author}
         [e] e))
