(defproject datascript-graph "0.1.0-SNAPSHOT"
  :description "Defines a datascript implementation of the IGraph protocol."
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/clojurescript "1.9.227"]
                 [com.taoensso/timbre "4.10.0"]
                 [ont-app/igraph "0.1.4-SNAPSHOT"]
                 [datascript "0.18.1"]
                 ]
  :plugins [[lein-cljsbuild "1.1.7"
             :exclusions [[org.clojure/clojure]]]
            [lein-doo "0.1.10"]]
  :cljsbuild
  {:builds
   ;; for testing the cljs incarnation
   ;; run with 'lein doo firefox test', or swap in some other browser
   {:test {:source-paths ["src" "cljs-test"]
           :compiler {:output-to "resources/test/compiled.js"
                      ;; entry point for doo-runner:
                      :main igraph.browser ;; at cljs-test/igraph/browser.cljs
                      :optimizations :none
                      :warnings {:bad-method-signature false}
                      }}}
   }

  :main ^:skip-aot datascript-graph.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
