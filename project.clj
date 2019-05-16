;; Copyright 2017-2019 Acrolinx GmbH

;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
;; implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
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

  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.10.520"]]

  :plugins [[lein-doo "0.1.11"]
            [lein-cljsbuild "1.1.7"]]

  :cljsbuild
  {:builds {:tests {:source-paths ["src" "test"]
                    :compiler {:output-to "target/tests.js"
                               :main com.acrolinx.cljs-test-runner
                               :optimizations :simple}}}}
  :doo {:build "tests"
        :alias {:default [:nashorn]}})
