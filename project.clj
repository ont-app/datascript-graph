(defproject ont-app/datascript-graph "0.1.0-SNAPSHOT"
  :description "Defines a datascript implementation of the IGraph protocol."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/clojurescript "1.10.520"]
                 [org.clojure/spec.alpha "0.2.176"]
                 ;; [com.taoensso/timbre "4.10.0"]
                 [ont-app/graph-log "0.1.0-SNAPSHOT"]
                 [ont-app/igraph "0.1.4-SNAPSHOT"]
                 [datascript "0.18.8"]
                 ]
  :plugins [[lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-doo "0.1.10"]]
  :target-path "target/%s"
  :resource-paths ["resources" "target/cljsbuild"]
  :source-paths ["src"]
  :test-paths ["src" "test"]
  :cljsbuild
  {:test-commands {"test" ["lein" "doo" "node" "test" "once"]}
   :builds
   {:dev {:source-paths ["src"]
          :compiler {
                     :main ont-app.datascript-graph.core 
                     :asset-path "js/compiled/out"
                     :output-to "resources/public/js/datascript-graph.js"
                     :source-map-timestamp true
                     :output-dir "resources/public/js/compiled/out"
                     :optimizations :none
                     }
          }
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

 ;; :main ^:skip-aot datascript-graph.core

  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[binaryage/devtools "0.9.10"]
                                  ]
                   :source-paths ["src"] 
                   :clean-targets
                   ^{:protect false}
                   ["resources/public/js/compiled"
                    "resources/test"
                    :target-path
                    ]
                   }

             })
