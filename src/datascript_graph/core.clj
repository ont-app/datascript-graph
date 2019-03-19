(ns datascript-graph.core
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [datascript.core :as d]
   [datascript.db :as db]
   [igraph.core :as igraph :refer :all]
   [igraph.graph :as graph]
   #_[datascript.transit :as dt])
  (:gen-class))

(declare graph-union)
(declare graph-difference)
(declare graph-intersection)
(declare get-normal-form)
(declare get-subjects)
(declare query-for-p-o)
(declare query-for-o)
(declare ask-s-p-o)
(declare normalized-query-output)

(defrecord 
  ^{:doc "An IGraph compliant view on a Datascript graphs
With arguments [<db>
Where
<db> is an instance of a Datascript database.
"
    }
    DatascriptGraph [db]
  IGraph
  (normal-form [this] (get-normal-form db))
  (add [this to-add] (add-to-graph this to-add))
  (subjects [this] (get-subjects db))
  (get-p-o [this s] (query-for-p-o db s))
  (get-o [this s p] (query-for-o db s p))
  (ask [this s p o] (ask-s-p-o db s p o))
  (query [this query-spec] (normalized-query-output this query-spec))
  
  clojure.lang.IFn
  (invoke [g] (normal-form g))
  (invoke [g s] (get-p-o g s))
  (invoke [g s p] (match-or-traverse g s p))
  (invoke [g s p o] (match-or-traverse g s p o))

  ISet
  (union [g1 g2] (graph-union g1 g2))
  (difference [g1 g2] (graph-difference g1 g2))
  (intersection [g1 g2] (graph-intersection g1 g2))
  )

(def igraph-schema
  "The basic schema that informs the IGraph implmentation.
  ::id is the identifier for <s> arguments
  ::top is true for a subject with no elaborating attributes,
    (this will provide a basis for a dummy triple declaration for
    its reference).
  "
  {::id {:db/unique :db.unique/identity
         :db/doc "Identifies subjects"
         }
   ::top {:db/type
          :db.type/boolean
          :doc (str "Indicates entities which are not otherwise elaborated."
                    "Use this if you encounter a Nothing found for entity id <x>"
                    "error.")
          }
   })

(defn make-graph
  "Returns an instance of DatascriptGraph.
  Where
  <schema> must contain Datascript schema declaration for every <p>.
  See also <https://github.com/kristianmandrup/datascript-tutorial/blob/master/create_schema.md>
  "
  ([schema]
   (->DatascriptGraph (db/empty-db (merge schema igraph-schema))))
  ([]
   (make-graph {})))


(defn get-entity-id [db s]
  "Returns <e> for <s> in <g>
Where
<e> is the entity ID (a positive integer) in (:db <g>)
<s> is a subject s.t. [<e> ::id <s>] in (:db <g>)
<g> is an instance of DatascriptGraph
"
  (unique
   (d/q '[:find [?e]
          :in $ ?s
          :where [?e ::id ?s]]
        db s)))

(defn get-reference [db e]
  "Returns <s> for <e> in <g>
Where
<s> is a subject s.t. [<e> ::id <s>] in (:db <g>)
<e> is the entity ID (a positive integer) in (:db <g>)
<g> is an instance of DatascriptGraph
"
  (unique
   (d/q '[:find [?s]
          :in $ ?e
          :where [?e ::id ?s]]
        db e)))

(defn maybe-dereference [db a v]
  "Returns reference of <v> if <a> is :db.type/ref in <db>  else <v>
Where
<v> is a value in some datom of <db>
<a> is an attribute in some datom of <db>
<db> is a datascript db
"
  (if (= (:db/type ((:schema db) a))
         :db.type/ref)
    (get-reference db v)
    v))

;; Declared in igraph.core
(defmethod add-to-graph [DatascriptGraph :normal-form] [g triples]
  (let [s->db-id (into {}
                       (map (fn [s id] [s id])
                            (keys triples)
                            (map (comp - inc) (range))))
        ]
    (letfn [(get-id [s]
              (or (get-entity-id (:db g) s)
                  (s->db-id s)))
            
            (collect-datom [id p acc o]
              ;; returns {e :db/id <id>, <p> <o>}
              (let [o' (or (get-id o)
                           o)]
                (conj acc {:db/id id 
                           p o'})))
          
            (collect-p-o [id acc p o]
              ;; accumulates [<datom>...]
              (if (not (p (:schema (:db g))))
                (throw (Exception. (str "No schema for " p " in schema " (:schema (:db g)))))
                (reduce (partial collect-datom id p) acc o)))

            (collect-s-po [acc s po]
              ;; accumulates [<datom>...]
              (let [id (get-id s)
                    ]
                (reduce-kv (partial collect-p-o id)
                           (conj acc {:db/id id, ::id s})
                           po)))
            ]
      (assoc g :db
             (d/db-with (:db g)
                        (reduce-kv collect-s-po [] triples))))))


;; Declared in igraph.core
(defmethod add-to-graph [DatascriptGraph :vector-of-vectors] [g triples]
  (add-to-graph g
                (normal-form
                 (add (graph/make-graph)
                      (with-meta triples
                        {:triples-format :vector-of-vectors})))))
;; Declared in igraph.core
(defmethod add-to-graph [DatascriptGraph :vector] [g triple]
  (add-to-graph g
                (normal-form
                 (add (graph/make-graph)
                      (with-meta [triple]
                        {:triples-format :vector-of-vectors})))))

(defn- shared-keys [m1 m2]
  "Returns {<shared key>...} for <m1> and <m2>
Where
<shared key> is a key in both maps <m1> and <m2>
"
  (set/intersection (set (keys m1))
                    (set (keys m2))))


(defmethod remove-from-graph [DatascriptGraph :normalForm] [g to-remove]
  ;; (assoc g :db
  ;;        (d/db-with (:db g)
  ;;                   (reduce (par
  (shared-keys (g) to-remove)
  )



(defmethod remove-from-graph [DatascriptGraph :vector-of-vectors] [g to-remove]
  (letfn [(collect-remove-clause [acc v]
            (conj acc 
                  (case (count v)
                    1
                    (let [[s] v
                          ]
                      [:db/retractEntity [::id s]])
                    2
                    (let [[s p] v
                          ]
                      [:db.fn/retractAttribute [::id s] p])
                    3
                    (let [[s p o] v
                          o (if (= (:db/type ((:schema (:db g)) p))
                                   :db.type/ref)
                              [::id o]
                              o)
                          ]
                      [:db/retract [::id s] p o]))))
          ]
    (assoc g :db
           (d/db-with
            (:db g)
            (reduce collect-remove-clause
                    []
                    to-remove)))))


  
(defmethod remove-from-graph [DatascriptGraph :vector] [g to-remove]
  (remove-from-graph g (with-meta
                         [to-remove]
                         {:triples-format :vector-of-vectors})))
  

(defmethod remove-from-graph [DatascriptGraph :normal-form] [g to-remove]
  (letfn [(collect-o [acc o]
            ;; acc is [s p]
            (conj acc o)
            )
          (collect-p [s acc p]
            ;; acc is [s]
            (reduce collect-o
                    (conj acc p)
                    (get-in to-remove [s p]))
            )
          (collect-clause-for-s [acc s]
            ;; accumulates <spo> 
            (conj acc
                  (reduce (partial collect-p s)
                          [s]
                          (keys (get to-remove s)))))

          ]
    (remove-from-graph
     g
     (with-meta
       (graph/vector-of-triples (add (graph/make-graph) to-remove))
       {:triples-format :vector-of-vectors}))))

(defn get-subjects [db]
  "Returns [<s>...] for <db>
Where 
<s> is a subject s.t. [<e> ::id <s>] in <db>
<db> is a Datascript DB
"
  (map (fn [d] (.valAt d "v"))
       (d/datoms db :avet ::id)))



(defn get-normal-form [db]
  "Returns contents of <db> s.t. {<s> {<p> #{<o>...}...}...}
"
  (let [collect-datum
        (fn [acc datom]
          ;;#dbg
          (let [[s->po, e->av, e->s] acc
                [e a v t] datom
                ]
            ;; Maintain each of these mappings with each iteration
            ;; over the datom ...
            [
             ;; s->po
             (if (contains? e->s e)
               (let [s (e->s e)]
                 (assert (not= a ::id))
                 (assoc-in s->po
                           [s a]
                           (conj (get-in s->po
                                         [s a]
                                         #{})
                                 (maybe-dereference db a v))))
               ;;else no subject mapping yet
               (if (= a ::id)
                 ;; transfer over from e->av
                 ;; e->s is about to be mapped
                 (assoc s->po
                        v
                        (get e->av e {}))
                 ;; else no change
                 
                 s->po))
             ;; e->av
             ;; holds a/v until we see the subject mapping
             (if (and (not (contains? e->s e))
                      (not (= a ::id)))
               (assoc-in e->av
                         [e a]
                         (conj (get-in e->av
                                       [e a]
                                       #{})
                               (maybe-dereference db a v)))
               ;;else maybe e->s now maps to s
               (if (contains? e->s e)
                 (dissoc e->av e)
                 ;; else no change
                 e->av))
             ;; e->s
             ;; maps e to subject
             (if (= a ::id)
               (assoc e->s e v)
               ;; else no change
               e->s)
             ]))
        ]
    (with-meta
      (into {}
            (filter (fn [[k v]]
                      (not (empty? v)))
                    (first
                     (reduce collect-datum
                             [{} {} {}]
                             (d/datoms db :eavt)))))
      {:triples-format :normal-form})))


(defn query-for-p-o [db s]
  "returns {<p> #{<o> ...}...} for <s> in <db>
"
  (let [collect-p-o (fn [acc [p o]]
                      (if (= p ::id)
                        acc
                        (update acc p (fn [os]
                                        (conj (or os
                                                  #{})
                                              (maybe-dereference db p o)
                                              )))))
        ]
    (reduce collect-p-o {}
            (d/q '[:find ?p ?o
                   :in $ ?s
                   :where
                   [?e :datascript-graph.core/id ?s]
                   [?e ?p ?o]]
                 db s))))

(defn query-for-o [db s p]
  "Returns #{<o> ...} for <s> and <p> in <db>
"
  (let [collect-o (fn [acc [o]]
                    (conj (or acc #{})
                          (maybe-dereference db p o)))
        ]
    (reduce collect-o #{}
            (d/q '[:find ?o
                   :in $ ?s ?p
                   :where
                   [?e :datascript-graph.core/id ?s]
                   [?e ?p ?o]]
                 db s p))))

(defn ask-s-p-o [db s p o]
  "Returns true if [<s> <p> <o>] is in <db>
"
  (not (empty?
        (d/q '[:find ?e
               :in $ ?s ?p ?o
               :where
               [?e :datascript-graph.core/id ?s]
               [?e ?p ?o]]
             db s p o))))


(defn normalized-query-output [db query-spec]
  "Returns [<binding> ...] per igraph spec for <query-spec> posed to <db>
Where
<binding> := {<var> <value> ...} 
<query-spec> := either <query> without an ':in' clause or a map
  of the form {:query :args} if query has an ':in' clause
<db> is a datascript db
<var> is taken from the :from clause of <query>
<value> is a value bound to <var> in the query response
<query> is a datascript query vector
<args> := [<db> <arg> ...] binding to the :in clause of <query> if it exists, or
  := [<db>] otherwise
<arg> matches and element of any :in clause in <query>
"
  (let [q (if (map? query-spec)
            (:query query-spec)
            query-spec)
        args (if (map? query-spec)
               (:args query-spec)
               [db])
        collect-key (fn [ks result acc i]
                      ;; {<key> <value>...} for <i>
                      (assoc acc (ks i) (result i)))
        
        normalize (fn [ks acc result]
                    ;; {<key> <value> ...} for <value> in <result>
                    ;; <ks> := [<key1> ... <keyn>]
                    ;; <result> := [<val1> ... <valn>]
                    (assert (= (count ks)
                               (count result)))
                    (reduce (partial collect-key ks result)
                            acc
                            (range (count result))))
        find-clause (fn [q]
                      ;; returns [<key> ...]
                      (mapv (comp keyword :symbol)
                            (-> (datascript.parser/parse-query q)
                                :qfind
                                :elements)))

        ]
    (reduce (partial normalize (find-clause q))
            {}
            (apply d/q (vec (concat [q] args))))))

;; SET OPERATIONS
(defn graph-union [g1 g2]
  "Returns union of <g1> and <g2> using same schema as <g1>
This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (add (make-graph (merge (:schema (:db g1))
                          (:schema (:db g2))))
       (normal-form (union (graph/make-graph (g1))
                           (graph/make-graph (g2))))))
(defn graph-difference [g1 g2]
  "This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (add (make-graph (:schema (:db g1)))
       (normal-form (difference (graph/make-graph (g1))
                                (graph/make-graph (g2))))))

(defn graph-intersection [g1 g2]
  "This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (add (make-graph (:schema (:db g1)))
       (normal-form (intersection (graph/make-graph (g1))
                                  (graph/make-graph (g2))))))
  

