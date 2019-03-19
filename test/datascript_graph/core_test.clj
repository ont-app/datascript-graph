(ns datascript-graph.core-test
  (:require [clojure.test :refer :all]
            [igraph.core :refer :all]
            [datascript-graph.core :as dsg :refer :all]))


(def test-schema {
                  :isa {:db/type :db.type/ref
                        :db/cardinality :db.cardinality/many
                        }
                  :subClassOf {:db/type :db.type/ref
                               :db/cardinality :db.cardinality/many
                               }
                  :likes {:db/type :db.type/ref
                          :db/cardinality :db.cardinality/many
                          }
                  :name {:db/cardinality :db.cardinality/many
                         :db/type :db.type/ref
                         }
                  })

(def mini-content [[:john :isa :person]
                   [:person ::dsg/top true]])

(def test-content [[:john :isa :person]
                   [:john :likes :meat]
                   [:john :name :enForm:john]
                   [:enForm:john :isa :englishForm]
                   [:englishForm ::dsg/top true]
                   [:mary
                    :isa :person
                    :likes :coke
                    :name :enForm:mary
                    ]
                   [:enForm:mary :isa :englishForm]
                   [:likes :isa :property]
                   [:property ::dsg/top true]
                   [:isa :isa :property]
                   [:meat :isa :food]
                   [:coke :isa :drink]
                   [:drink :subClassOf :consumable]
                   [:food :subClassOf :consumable]
                   [:consumable :subClassOf :thing]
                   [:person :subClassOf :thing]
                   [:thing ::dsg/top true]
                   ])

(def mini-graph (add (make-graph test-schema)
                     mini-content))

(def test-graph (add (make-graph test-schema)
                     test-content))



(deftest a-test
  (testing "FIXME, I fail."
    (is (= 0 1))))
