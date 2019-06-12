(ns datascript-graph.core
  (:require
   [clojure.set :as set]
   ;; [clojure.string :as str]
   [datascript.core :as d]
   [datascript.db :as db]
   [igraph.core :as igraph]
   [igraph.graph :as graph]
   ;;[taoensso.timbre :as log]
   #_[datascript.transit :as dt])
  ;; (:gen-class)
  )


(defn error [msg]
  #?(:clj (Exception. msg)
     :cljs (js/Error msg)))

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
  igraph/IGraph
  (normal-form [this] (get-normal-form db))
  (add [this to-add] (igraph/add-to-graph this to-add))
  (subtract [this to-subtract] (igraph/remove-from-graph this to-subtract))
  (subjects [this] (get-subjects db))
  (get-p-o [this s] (query-for-p-o db s))
  (get-o [this s p] (query-for-o db s p))
  (ask [this s p o] (ask-s-p-o db s p o))
  (query [this query-spec] (normalized-query-output db query-spec))
  
  #?(:clj clojure.lang.IFn
     :cljs cljs.core/IFn)
  (invoke [g] (igraph/normal-form g))
  (invoke [g s] (igraph/get-p-o g s))
  (invoke [g s p] (igraph/match-or-traverse g s p))
  (invoke [g s p o] (igraph/match-or-traverse g s p o))

  igraph/IGraphSet
  (union [g1 g2] (graph-union g1 g2))
  (difference [g1 g2] (graph-difference g1 g2))
  (intersection [g1 g2] (graph-intersection g1 g2))
  )

(defn get-valAt [d avet]
  "Calls (.valAt d avet) or (-lookup d avet nil) appropriate to clj/cljs
Where
<d> is a datom
<avet> is a char, one of 'avet'
"
  #?(:clj (.valAt d avet)
     :cljs (-lookup d avet nil)))

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
  (d/entity db s))

(defn get-reference [db e]
  "Returns <s> for <e> in <g>
Where
<s> is a subject s.t. [<e> ::id <s>] in (:db <g>)
<e> is the entity ID (a positive integer) in (:db <g>)
<g> is an instance of DatascriptGraph
"
  
  (igraph/unique
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


(defmethod igraph/add-to-graph [DatascriptGraph :normal-form] [g triples]
  (if (empty? triples)
    g
    (let [s->db-id (atom
                    (into {}
                          (map (fn [s id] [s id])
                               (keys triples)
                               (map (comp - inc) (range)))))
          schema (:schema (:db g))
          check-o (fn [p o]
                    ;; returns p of o checks out else throws error
                    (when (some (complement keyword?) o)
                      (throw (error
                              (str "No schema declaration for "
                                   p
                                   " and "
                                   o
                                   " contains non-keyword. "
                                   "(Will only auto-declare for refs)"))))
                    p)
          ;; find p's with no schema decl...
          no-schema (reduce-kv (fn [acc s po]
                                 (reduce-kv (fn [acc p o]
                                              (if (schema p)
                                                acc
                                                (conj acc (check-o p o))))
                                            acc
                                            po
                                            ))
                               #{}
                               triples)
          
          ]
      (letfn [(get-id [db s]
                ;; returns nil or {::value ... ::new?...}
                ;; new? means there was a keyword in object
                ;; position not covered by a subject in the
                ;; triples
                (if-let [id (get-entity-id db s)]
                  (do
                    {::value id
                     ::new? false})
                  (if-let [id (@s->db-id s)]
                    (do
                      {::value id
                       ::new? false})
                    ;; else this is an object with no id
                    (if (keyword? s)
                      ;; we need to add a new id for <s>
                      (do (swap! s->db-id
                                 (fn [m]
                                   (assoc m s
                                          (- (inc (count m))))))
                          {::value (@s->db-id s)
                           ::new? true}))
                    )))
              
              (collect-datom [db id p acc o]
                ;; returns {e :db/id <id>, <p> <o>}
                (let [db-id (get-id db o)
                      new? (and (::value db-id) (::new? db-id))
                      o' (or (::value db-id)
                             o)
                      ]
                  (conj (if new?
                          (conj acc {:db/id o' ::id o})
                          acc)
                        {:db/id id p o'})))
              
              (collect-p-o [db id acc p o]
                ;; accumulates [<datom>...]
                (reduce (partial collect-datom db id p) acc o))


              (collect-s-po [db acc s po]
                ;; accumulates [<datom>...]
                (let [id (::value (get-id db s))
                      ]
                  (reduce-kv (partial collect-p-o db id)
                             (conj acc {:db/id id, ::id s})
                             po)))
              
              (update-schema [db]
                ;; Intalls default schema declaration for new refs
                (-> db
                    (update 
                     :schema
                     (fn [schema]
                       (reduce (fn [s p]
                                 (assoc s p
                                        {:db/type :db.type/ref
                                         :db/cardinality :db.cardinality/many
                                         }))
                               schema
                               no-schema)))
                    (update
                     :rschema
                     (fn [rschema]
                       (reduce (fn [r p]
                                 (->
                                  r
                                  (update :db/index #(conj (or % #{})  p))
                                  (update :db.type/ref #(conj (or % #{}) p))
                                  (update :db.cardinality/many
                                          #(conj (or % #{}) p))))
                               rschema
                               no-schema)))))

              
              ]
        (let [db' (update-schema (:db g))
              ]
          (assoc g :db
                 (d/db-with db'
                            (reduce-kv (partial collect-s-po db') [] triples))))))))


;; Declared in igraph.core
(defmethod igraph/add-to-graph [DatascriptGraph :vector-of-vectors] [g triples]
  (if (empty? triples)
    g
    (igraph/add-to-graph g
                         (igraph/normal-form
                          (igraph/add (graph/make-graph)
                                      (with-meta triples
                                        {:triples-format :vector-of-vectors}))))))
;; Declared in igraph.core
(defmethod igraph/add-to-graph [DatascriptGraph :vector] [g triple]
  (if (empty? triple)
    g
    (igraph/add-to-graph
     g
     (igraph/normal-form
      (igraph/add (graph/make-graph)
                  (with-meta [triple]
                    {:triples-format :vector-of-vectors}))))))

(defn- shared-keys [m1 m2]
  "Returns {<shared key>...} for <m1> and <m2>
Where
<shared key> is a key in both maps <m1> and <m2>
"
  (set/intersection (set (keys m1))
                    (set (keys m2))))


(defmethod igraph/remove-from-graph [DatascriptGraph :vector-of-vectors]
  [g to-remove]
  (if (empty? to-remove)
    g
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
                      to-remove))))))

  
(defmethod igraph/remove-from-graph [DatascriptGraph :vector]
  [g to-remove]
  (if (empty? to-remove)
    g
    (igraph/remove-from-graph g (with-meta
                                  [to-remove]
                                  {:triples-format :vector-of-vectors}))))
  

(defmethod igraph/remove-from-graph [DatascriptGraph :normal-form]
  [g to-remove]
  (if (empty? to-remove)
    g
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
      (igraph/remove-from-graph
       g
       (with-meta
         (graph/vector-of-triples (igraph/add (graph/make-graph) to-remove))
         {:triples-format :vector-of-vectors})))))

(defn get-subjects [db]
  "Returns [<s>...] for <db>
Where 
<s> is a subject s.t. [<e> ::id <s>] in <db>
<db> is a Datascript DB
"
  (map (fn [d] (get-valAt d "v"))
       (d/datoms db :avet ::id)))

(defn get-normal-form [db]
  "Returns contents of <db> s.t. {<s> {<p> #{<o>...}...}...}
"
  (let [collect-datom
        (fn [acc datom]
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
            ;; remove subtraction-dregs
            (filter (fn [[k v]]
                      (not (empty? v)))
                    (first
                     (reduce collect-datom
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
   ((query-for-o db s p) o))


(defn normalized-query-output 
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
  [db query-spec]
  {:pre [(= (type db) datascript.db.DB)]
   }
  (let [q (if (map? query-spec)
            (:query query-spec)
            query-spec)
        args (if (map? query-spec)
               (:args query-spec)
               [db])
        collect-key (fn [ks result acc i]
                      ;; {<key> <value>...} for <i>
                      (assoc acc (ks i) (result i)))
        
        normalize (fn [ks result]
                    ;; {<key> <value> ...} for <value> in <result>
                    ;; <ks> := [<key1> ... <keyn>]
                    ;; <result> := [<val1> ... <valn>]
                    (assert (= (count ks)
                               (count result)))
                    (reduce (partial collect-key ks result)
                            {}
                            (range (count result))))
        find-clause (fn [q]
                      ;; returns [<key> ...]
                      (mapv (comp keyword :symbol)
                            (-> (datascript.parser/parse-query q)
                                :qfind
                                :elements)))

        ]
    (map (partial normalize (find-clause q))
         (apply d/q (vec (concat [q] args))))))

;; SET OPERATIONS
(defn graph-union [g1 g2]
  "Returns union of <g1> and <g2> using same schema as <g1>
This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (igraph/add (make-graph (merge (:schema (:db g1))
                                 (:schema (:db g2))))
       (igraph/normal-form (igraph/union
                            (igraph/add (graph/make-graph) (g1))
                            (igraph/add (graph/make-graph) (g2))))))

(defn graph-difference [g1 g2]
  "This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (igraph/add (make-graph (:schema (:db g1)))
              (igraph/normal-form
               (igraph/difference
                (igraph/add (graph/make-graph) (g1))
                (igraph/add (graph/make-graph) (g2))))))

(defn graph-intersection [g1 g2]
  "This uses igraph.graph/Graph as scratch, and probably won't scale.
TODO:Redo when you have data to develop for scale.
"
  (igraph/add (make-graph (:schema (:db g1)))
              (igraph/normal-form
               (igraph/intersection
                (igraph/add (graph/make-graph) (g1))
                (igraph/add (graph/make-graph) (g2))))))
  

