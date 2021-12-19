# datascript-graph

This is an implementation of the
[IGraph](https://github.com/ont-app/igraph) protocol extended to
[datascript](https://github.com/tonsky/datascript). It should work
under both clj and cljs.

The general idea here is to provide a standard container in clojure
for labeled directed graphs:

```
(g) -> {s {p #{o}}}
(g s) -> {p #{o}}
(g s p) -> #{o}
(g s p o) -> <truthy>
```

Where `s` `p` and `o` are respectively `subject`, `predicate`, and `object`.


## Installation

This is available on [Clojars](https://clojars.org/ont-app/datascript-graph):

[![Clojars Project](https://img.shields.io/clojars/v/ont-app/datascript-graph.svg)](https://clojars.org/ont-app/datascript-graph)

Additional documentation is available at https://cljdoc.org/d/ont-app/datascript-graph/0.1.0 .

## Usage

For purposes of this discussion, we'll assume the following namespace declarations:
```
(ns user
  (:require 
    [ont-app.igraph.core :as igraph :refer [add, unique, query]]
    [ont-app.datascript-graph.core :as dsg :refer [make-graph]]))

```
A new graph is created with `make-graph`. In the simplest case it may
take zero arguments:

```
(def g (make-graph))

``` 

This will give you access to the standard operations described at
[ont-app/igraph](https://github.com/ont-app/igraph). 
```
(def g 
  (add g 
      [[:john 
        :isa :person 
        :likes :pizza]
        [:mary
        :isa :person
        :likes :pasta]]))

(g)
;; -> 
;; {:john {:isa #{:person}, :likes #{:pizza}}, 
;;  :mary {:isa #{:person}, :likes #{:pasta}}}

(g :john)
;;->
;; {:isa #{:person}
;;  :likes #{:pizza}}
 
(g :john :likes)
;;->
;; #{:pizza}

(unique (g :john :isa))
;; ->
;; :person

(g :mary :likes :pasta) ;; expect truthy response
:; ->
;; :pasta
         
```

See the [IGraph definition](https://github.com/ont-app/igraph) for a
full description of the IGraph protocol.

You can get the native datascript object with `:db`...

```
(:db g)
;;=> 
;; #datascript/DB {:schema {:ont-app.datascript-graph.core/id #:db{:unique :db.unique/identity, :doc "Identifies subjects"}, :ont-app.datascript-graph.core/top {:db/type :db.type/boolean, :doc "Indicates entities which are not otherwise elaborated.Use this if you encounter a Nothing found for entity id <x>error."}, :isa #:db{:type :db.type/ref, :cardinality :db.cardinality/many}, :likes #:db{:type :db.type/ref, :cardinality :db.cardinality/many}}, :datoms [[1 :isa 2 536870913] [1 :likes 3 536870913] [1 :ont-app.datascript-graph.core/id :john 536870913] [2 :ont-app.datascript-graph.core/id :person 536870913] [3 :ont-app.datascript-graph.core/id :pizza 536870913] [4 :isa 2 536870913] [4 :likes 5 536870913] [4 :ont-app.datascript-graph.core/id :mary 536870913] [5 :ont-app.datascript-graph.core/id :pasta 536870913]]}

```

## Datascript Schemas

Datascript DBs are informed by [schemas](https://github.com/kristianmandrup/datascript-tutorial/blob/master/create_schema.md). The rest of this discussion
presumes familiarity with such.

You can access the schema for `g` with `(:schema (:db g))`.

The `DB` object in datascript-graphs is initialized with a minimal
default schema.

```
{
  :ont-app.datascript-graph.core/id 
  {
    :db/unique :db.unique/identity
  }
  :ont-app.datascript-graph.core/top
  {
    :db/type :db.type/boolean
  }
```

The `:ont-app.datascript-graph.core/id` property is used to assign the `s`
identifier to the associated datascript record.

`:ont-app.datascript-graph.core/top` exists as a kind of dummy property that
can be used in rare cases to keep a given subject from being an
orphan, you may want to use this if you encounter a "Nothing found for
entity id <x>" error. The name Top is chosen because it's assumed that
in normal use entities would be given at minimum some kind of type or
other subsumption link, unless this entity is at the top of the
heirarchy.

You can optionally supply your own schema at initialization time:
```
(make-graph {
             :likesTacos 
             {
               :db/type :db.type/boolean 
               :doc "True if subject likes tacos"
             }})
```

### Automatic schema declarations

If a triple is added with a predicate which does not have a schema
declaration, and if the object is a keyword (and therefore assumed to be
an identifier), an entry will be added automatically with these defaults:

```
{ 
  <predicate> 
  {
    :db/type :db.type/ref
    :db/cardinality :db.cardinality/many
  }
 }
```

An error will be thrown if a triple whose predicate is not declared in
the schema when the object is not a keyword/ref.

```
(add g [[:john :likesBurritos false]])
;; -> 
;; Exception No schema declaration for :likesBurritos and #{false} contains non-keyword. (Will only auto-declare for refs) ...

```

Note that declaring a predicate to be :db/type :db.type/ref indicates
that the object of that predicate it taken to be a reference to some
other record in the database.

See the [datascript](https://github.com/tonsky/datascript) documentation for more on schemas and other conventions.

## Querying

The IGraph query function expects queries in a format appropriate to
the native representation, in this case [Datalog](http://www.learndatalogtoday.org/):

```
(query g '[:find ?person 
           :where 
           [?e :isa ?_person]
           [?_person ::dsg/id :person]
           [?e ::dsg/id ?person]])
;; ->
({:?person :mary} {:?person :john})
```

Note that per the IGraph protocol, the return value is a sequence of
variable binding maps.

## License

Copyright © 2019-20 Eric D. Scott

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
