(ns chouser.victoria
  (:gen-class)
  (:require ring.middleware.resource
            ring.middleware.content-type
            ring.middleware.not-modified
            [ring.adapter.jetty :as jetty]
            [clojure.data.json :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(defn default-handler [{:keys [^String uri] :as request}]
  {:default? true
   :status 200
   :headers {"Content-Type" "text/html"}
   :body (str "<h1>Victoria!</h1>"
              "<pre>"
              (with-out-str (pprint request))
              "</pre>")})

(defn wrap-dir-file [handler filename]
  (fn [request]
    (let [response (handler request)]
      (if (:default? response)
        (handler (update request :uri str filename))
        response))))

(defn scale ^long [^long pos, ^long low, ^long high]
  (long (+ low (/ (* pos (- high low)) 1000))))

(def json-reply
  (memoize
   (fn [obj]
     {:status 200
      :header {"Content-Type" "text/json"}
      :body (json/write-str obj :key-fn #(str/replace (name %) #"-" "_"))})))

(defn wrap-boat-ctrls [handler, config,
                       ^java.io.BufferedWriter servo-writer]
  (fn [{:keys [request-method body uri] :as request}]
    (cond
     (and (= uri "/ctrl") (= request-method :post))
     (let [{:keys [sails rudder]} (json/read-str (slurp body) :key-fn keyword)]
       (.write servo-writer
               (format "0=%dus\n1=%dus\n"
                       (apply scale sails  (-> config :sails  :servo-range))
                       (apply scale rudder (-> config :rudder :servo-range))))
       (.flush servo-writer)
       (json-reply {}))
     (and (= uri "/config") (= request-method :get))
     (json-reply {:config config})
     :else (handler request))))

(defn wrap-canonical-uri [handler]
  (fn request [{:keys [uri] :as request}]
    (loop [previous-uri uri]
      (let [simpler-uri (str/replace previous-uri #"/\.\./[^/]+|//+" "/")]
        (if (not= simpler-uri previous-uri)
          (recur simpler-uri)
          (handler (assoc request :uri previous-uri)))))))

(defn app [config]
  (let [servo-writer (io/writer (:servo-dev config))]
    (-> default-handler
        (ring.middleware.resource/wrap-resource "public")
        (ring.middleware.content-type/wrap-content-type)
        (ring.middleware.not-modified/wrap-not-modified)
        (wrap-boat-ctrls config servo-writer)
        (wrap-dir-file "/index.html")
        (wrap-canonical-uri))))

(defn -main
  ([] (-main "80"))
  ([port]
     (let [config (read-string (slurp "victoria-conf.clj"))]
       (jetty/run-jetty (app config) {:port (Long/parseLong port)}))))
