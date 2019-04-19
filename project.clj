(defproject com.acrolinx/clj-queue-by "0.1.2-SNAPSHOT"
  :description "A queue which schedules fairly by key."
  :url "http://github.com/acrolinx/clj-queue-by"
  :license {:name "Apache License 2.0"
            :url "http://www.apache.org/licenses/"}

  :deploy-repositories [["releases"
                         {:url "https://clojars.org/repo"
                          :password :env/CLOJARS_PASS
                          :username :env/CLOJARS_USER
                          :sign-releases false}]
                        ["snapshots"
                         {:url "https://clojars.org/repo"
                          :password :env/CLOJARS_PASS
                          :username :env/CLOJARS_USER}]]

  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/clojurescript "1.10.520"]]

  :plugins [[lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.7"]]

  :cljsbuild {:builds {:tests {:source-paths ["src" "test"]
                               :compiler {:output-to "target/tests.js"
                                          :main com.acrolinx.cljs-test-runner
                                          :optimizations :simple}}}}
  :doo {:build "tests"
        :alias {:default [:nashorn]}})
