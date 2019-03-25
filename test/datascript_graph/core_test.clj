(ns datascript-graph.core-test
  (:require [clojure.test :refer :all]
            [igraph.core :refer :all]
            [igraph.graph :as graph]
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

(def mini-graph (add (make-graph)
                     mini-content))
 

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

(def test-graph (add (make-graph)
                     test-content))

(def standard-graph (add (graph/make-graph)
                         test-content))

^traversal-fn
(def subClassOf* (transitive-closure :subClassOf))

^traversal-fn
(defn isa->subClassOf* [g context acc queue]
  [context
   (->> queue 
        (traverse g (traverse-link :isa) (assoc (dissoc context :seek)
                                                :phase :isa) #{})
        vec
        (traverse g (transitive-closure :subClassOf) (assoc context :phase :sc)
                  #{}))
   []])

(deftest graph-equivalence-test
  (testing "Most functions should be equivalent to igraph.graph"
    (is (= (test-graph) (standard-graph)))
    (is (= (normal-form test-graph) (normal-form standard-graph)))
    (is (= (set (subjects test-graph))
           (set (subjects standard-graph))))
    (is (= (test-graph :john) (standard-graph :john)))
    (is (= (test-graph :john :likes) (standard-graph :john :likes)))
    (is (= (test-graph :john :likes :meat) (standard-graph :john :likes :meat)))
    (is (= (test-graph :drink subClassOf* :consumable)
           (standard-graph :drink subClassOf* :consumable)))
    (is (= (test-graph :drink subClassOf*)
           (standard-graph :drink subClassOf*)))
    (let [args [:john]]
      (is (= (normal-form (subtract test-graph args))
             (normal-form (subtract standard-graph args)))))

    (let [args [:john :likes]]
      (is (= (normal-form (subtract test-graph args))
             (normal-form (subtract standard-graph args)))))
    (let [args [:john :likes :meat]]
      (is (= (normal-form (subtract test-graph args))
             (normal-form (subtract standard-graph args)))))
    (let [args [[:john :likes :meat]
                [:mary :name]]]
      (is (= (normal-form (subtract test-graph args))
             (normal-form (subtract standard-graph args)))))
    (let [
          contents-1 [[:a :b :c :d :e]
                      [:f :g :h :i :j]
                      ]
          contents-2 [[:a :b :c]
                      [:f :g :h :k :l]
                      ]
          g1 (add (make-graph) contents-1)
          s1 (add (graph/make-graph) contents-1)
          g2 (add (make-graph) contents-2)
          s2 (add (graph/make-graph) contents-2)
          
          ]
      (is (= (normal-form (union g1 g2))
             (normal-form (union s1 s2))))
      (is (= (normal-form (intersection g1 g2))
             (normal-form (intersection s1 s2))))
      (is (= (normal-form (difference g1 g2))
             (normal-form (difference s1 s2))))
      (is (= (test-graph :coke isa->subClassOf*)
             (standard-graph :coke isa->subClassOf*)))
                                              
    )))


(deftest query-test
  (testing "Native queries should work as expected"
    (is (= (set (query test-graph
                       '[:find ?liker ?liked
                         :where
                         [?_liker ::dsg/id ?liker]
                         [?_liker :likes ?_liked]
                         [?_liked ::dsg/id ?liked]]))
           #{{:?liker :john, :?liked :meat}
             {:?liker :mary, :?liked :coke}}))))
