(defproject ont-app/datascript-graph "0.1.1"
  :description "Defines a datascript implementation of the IGraph protocol."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[ont-app/graph-log "0.1.5"]
                 ;; 3rd party libs:
                 [datascript "1.3.2"]
                 ]
  :plugins [[lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-codox "0.10.6"]
            [lein-doo "0.1.10"]]
  :target-path "target/%s"
  :resource-paths ["resources" "target/cljsbuild"]
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :cljsbuild
  {:test-commands {"test" ["lein" "doo" "node" "test" "once"]}
   :builds
   {
   ;; for testing the cljs incarnation
   ;; run with 'lein doo firefox test once', or swap in some other browser
   :test {:source-paths ["src" "test"]
           :compiler {
                      ;; entry point for doo-runner:
                      :main ont-app.datascript-graph.doo
                      :target :nodejs
                      :asset-path "resources/test/js/compiled/out"
                      :output-to "resources/test/compiled.js"
                      :output-dir "resources/test/js/compiled/out"
                      :optimizations :none ;;:advanced ;;:none
                      :warnings {:bad-method-signature false}
                      }}}
   } ;; cljsbuild


  :profiles {:uberjar {}
             :dev {:dependencies
                   [[org.clojure/clojure "1.10.3"]
                    [org.clojure/clojurescript "1.10.896"]
                    [lein-doo "0.1.11"]
                    ]}
             }
  :codox {:output-path "doc"}
  :clean-targets ^{:protect false} ["resources/public/js/compiled"
                                    "resources/test"
                                    :target-path]

  )  
