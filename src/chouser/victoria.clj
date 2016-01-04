(ns chouser.victoria
  (:gen-class)
  (:require ring.middleware.resource
            ring.middleware.content-type
            ring.middleware.not-modified
            [ring.adapter.jetty :as jetty]
            [clj-json.core :as json]
            [clojure.pprint :refer [pprint]]))

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

(defn wrap-boat-ctrls [handler]
  (fn [{:keys [request-method body] :as request}]
    (if (not= :post request-method)
      (handler request)
      (do
        ;; TODO servo control goes here
        (prn (json/parse-string (slurp body)))
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
