(defproject chouser/victoria "0.0.1"
  :dependencies [[org.clojure/clojure "1.8.0-RC3"]
                 [clj-json "0.5.3"]
                 [javax.servlet/servlet-api "2.5"]
                 [ring/ring-core "1.4.0"]
                 [ring/ring-jetty-adapter "1.4.0"]]
  :profiles {:uberjar {:aot :all}}
  :main chouser.victoria)
