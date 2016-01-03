;; rlwrap java -cp cljs.jar:src clojure.main browser-repl.clj

(require 'cljs.repl)
(require 'cljs.build.api)
(require 'cljs.closure)
(require 'cljs.repl.browser)
(require 'cljs.analyzer)

(cljs.build.api/build "src"
  {:main 'chouser.victoria
   :output-dir "resources/public/js"
   :output-to "resources/public/js/main.js"
   :asset-path "js"
   :verbose true})

(let [build cljs.closure/build
      repl-env (cljs.repl.browser/repl-env :port 9000)]
  (with-redefs [cljs.closure/build (fn [source opts compiler-env]
                                     (binding [cljs.analyzer/*cljs-ns* 'cljs.user]
                                       (let [rtn (build source opts compiler-env)]
                                         (cljs.repl/evaluate-form
                                          repl-env @compiler-env "auto-reload"
                                          `(~'ns cljs.user (:require ~(with-meta '[chouser.victoria]
                                                                        {:reload :reload}))))
                                         rtn)))]
    (cljs.repl/repl repl-env
                    :watch "src"
                    :output-dir "resources/public/js")))

