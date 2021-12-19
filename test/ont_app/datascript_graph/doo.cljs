(ns ont-app.datascript-graph.doo
  (:require [doo.runner :refer-macros [doo-tests]]
            [ont-app.datascript-graph.core-test]
            ))

(doo-tests
 'ont-app.datascript-graph.core-test
 )
