(ns chouser.boat-controller
  (:require ring.middleware.resource
            ring.middleware.content-type
            ring.middleware.not-modified
            [ring.adapter.jetty :as jetty]
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

(def app
  (-> default-handler
      (ring.middleware.resource/wrap-resource "public")
      (ring.middleware.content-type/wrap-content-type)
      (ring.middleware.not-modified/wrap-not-modified)
      (wrap-dir-file "/index.html")))

(defn main []
  (jetty/run-jetty #'app {:port 8000}))
