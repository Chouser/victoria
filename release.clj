;; java -cp cljs.jar:src clojure.main release.clj

(require 'cljs.repl)
(require 'cljs.build.api)
(require 'cljs.closure)

(cljs.build.api/build "src"
  {:output-dir "resources/public/js"
   :output-to "resources/public/js/main.js"
   :asset-path "js"
   :optimizations :advanced})

(System/exit 0)
