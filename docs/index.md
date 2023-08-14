# Tiler

[![JVM Tests](https://github.com/tunjid/Tiler/actions/workflows/tests.yml/badge.svg)](https://github.com/tunjid/Tiler/actions/workflows/tests.yml)
![Tiler](https://img.shields.io/maven-central/v/com.tunjid.tiler/tiler?label=tiler)

![badge][badge-ios]
![badge][badge-js]
![badge][badge-jvm]
![badge][badge-linux]
![badge][badge-windows]
![badge][badge-mac]
![badge][badge-tvos]
![badge][badge-watchos]

Please note, this is not an official Google repository. It is a Kotlin multiplatform experiment that makes no guarantees
about API stability or long term support. None of the works presented here are production tested, and should not be
taken as anything more than its face value.

## Introduction

### Tiling as a paging as state implementation

Tiling is a state based paging implementation that presents a sublist of paged dataset in a simple `List`.
It offers constant time access to items at indices, and the ability to introspect the items paged through.

Tiling is achieved with a Tiler; a pure function that has the ability to adapt any generic method of the form:

```kotlin
fun <T> items(query: Query): Flow<List<T>>
```

into a paginated API.

It does this using a `Tiler`:

```kotlin
fun interface ListTiler<Query, Item> {
    fun produce(inputs: Flow<Tile.Input<Query, Item>>): Flow<TiledList<Query, Item>>
}
```

* The [inputs](/implementation/primitives#inputrequest) modify the queries for data
* The output is the data returned over time in a `List` implementation: A `TiledList`.

For most practical purposes, the `TiledList` produced will be anchored or
["pivoted"](/implementation/pivoted-tiling) around a particular query for data.

See the [basic example](/usecases/basic-example) for a pivoted paging pipeline.

#### TiledList

A `TiledList` is a `List` that:

* Allows for looking up the query that fetched each item.
* Is a sublist of the items in the backing data source.

The former is done by associating a range of indices in the `List` with a `Tile`.
Effectively a `TiledList` "chunks" its items by query.
For example, the `TiledList` below is a `List` with 10 items, and two tiles. Each `Tile` covers 5 indices:

```
|     1      |        2      |
[0, 1, 2, 3, 4, 5, 6, 7, 8, 9]
```

A `Tile` is a `value` class with the following public properties:

```kotlin
value class Tile(...) {
    // start index for a chunk
    val start: Int
    // end exclusive index for a chunk
    val end: Int
}
``` 

A `TiledList` is defined as:

```kotlin
interface TiledList<Query, Item> : List<Item> {
  /**
   * The number of [Tile] instances or query ranges there are in this [TiledList]
   */
  val tileCount: Int

  /**
   * Returns the [Tile] at the specified tile index.
   */
  fun tileAt(tileIndex: Int): Tile

  /**
   * Returns the query at the specified tile index.
   */
  fun queryAtTile(tileIndex: Int): Query

  /**
   * Returns the query that fetched an [Item] at a specified index.
   */
  fun queryAt(index: Int): Query
}
```

`MutableTiledList` instances also exist:

```kotlin
interface MutableTiledList<Query, Item> : TiledList<Query, Item> {
    fun add(index: Int, query: Query, item: Item)

    fun add(query: Query, item: Item): Boolean

    fun addAll(query: Query, items: Collection<Item>): Boolean

    fun addAll(index: Int, query: Query, items: Collection<Item>): Boolean

    fun remove(index: Int): Item
}
```

This is useful for modifying `TiledList` instances returned. Actions like:

* Inserting separators or other interstitial content
* Mapping items with in memory data after fetching from a database
* General list modification

can be easily performed.

## Demo

The demo app is cheekily implemented as a dynamic grid of tiles with dynamic colors that update many times per second:

![Demo image](https://github.com/tunjid/tiler/blob/develop/misc/demo.gif)


## Get it

`Tiler` is available on mavenCentral with the latest version indicated by the badge at the top of this readme file.

`implementation com.tunjid.tiler:tiler:version`

## License

    Copyright 2021 Google LLC

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[badge-android]: http://img.shields.io/badge/-android-6EDB8D.svg?style=flat

[badge-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat

[badge-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat

[badge-js-ir]: https://img.shields.io/badge/support-[IR]-AAC4E0.svg?style=flat

[badge-nodejs]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat

[badge-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat

[badge-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat

[badge-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat

[badge-apple-silicon]: http://img.shields.io/badge/support-[AppleSilicon]-43BBFF.svg?style=flat

[badge-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat

[badge-mac]: http://img.shields.io/badge/-macos-111111.svg?style=flat

[badge-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat

[badge-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
