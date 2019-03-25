(defproject datascript-graph "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [ont-app/igraph "0.1.4-SNAPSHOT"]
                 [datascript "0.18.1"]
                 ]
  :main ^:skip-aot datascript-graph.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
