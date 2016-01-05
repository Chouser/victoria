(ns chouser.victoria
  (:gen-class)
  (:require ring.middleware.resource
            ring.middleware.content-type
            ring.middleware.not-modified
            [ring.adapter.jetty :as jetty]
            [clj-json.core :as json]
            [clojure.pprint :refer [pprint]]
            [clojure.java.io :as io]))

(set! *warn-on-reflection* true)

(def ^java.io.BufferedWriter servo-dev (io/writer "dev-servoblaster"))

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

(defn wrap-boat-ctrls [handler]
  (fn [{:keys [request-method body] :as request}]
    (if (not= :post request-method)
      (handler request)
      (let [{:strs [sails rudder]} (json/parse-string (slurp body))]
        (.write servo-dev (format "0=%dus\n1=%dus\n"
                                  (scale sails 1000 2000)
                                  (scale rudder 500 3000)))
        (.flush servo-dev)
        {:status 200
         :headers {"Content-Type" "text/html"}
         :body "ok"}))))

(def app
  (-> default-handler
      (ring.middleware.resource/wrap-resource "public")
      (ring.middleware.content-type/wrap-content-type)
      (ring.middleware.not-modified/wrap-not-modified)
      (wrap-boat-ctrls)
      (wrap-dir-file "/index.html")))

(defn -main
  ([] (-main "80"))
  ([port]
     (jetty/run-jetty #'app {:port (Long/parseLong port)})))
