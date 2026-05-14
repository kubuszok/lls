# Low Level Scala (lls)

Cross-platform (JVM, Scala.js, Scala Native) low-level utilities for Scala 3, focused on avoiding allocations, boxing, and reflection.

## Origins

This library extracts and optimizes core data structures originally ported from [libGDX](https://github.com/libgdx/libgdx) to Scala 3 as part of [SGE (Scala Game Engine)](https://github.com/kubuszok/sge). The goal is to provide these structures as a standalone, dependency-free library usable by any Scala 3 project — not just game engines.

## Key Features

### Allocation-conscious collections
- **`DynamicArray[A]`** — resizable array with unboxed primitive backing via `MkArray`, snapshot/copy-on-write semantics
- **`ObjectMap[K, V]`** / **`ObjectSet[A]`** — open-addressing hash map/set with Fibonacci hashing and backward-shift deletion
- **`OrderedMap[K, V]`** / **`OrderedSet[A]`** — insertion-ordered variants backed by `ObjectMap`/`ObjectSet` + `DynamicArray`
- **`ArrayMap[K, V]`** — linear-scan map using parallel key/value arrays, fast for small sizes

### Zero-boxing type class: `MkArray`
Sealed trait hierarchy with final subclasses per primitive type (`OfInts`, `OfLongs`, `OfFloats`, etc.). Each generates specialized JVM methods (`int get(int[], int)`) alongside erased bridges, eliminating `ScalaRunTime.array_apply` reflection and primitive boxing.

**`MkArray.withResolved`** uses `summonFrom` + polymorphic function types to preserve the concrete subclass type through inline method bodies, enabling fully specialized element access at call sites.

### Zero-allocation array views: `ArrayView`
Opaque type over `Array[A]` with type-level boolean parameters encoding view state (IArray vs Array, zipWithIndex vs plain). All methods are `inline`, eliminating lambda, tuple, and wrapper allocation in for-comprehensions:

```scala
for {
  (elem, i) <- array.leanView.zipWithIndex
  if i % 2 == 0
} yield elem * 10
```

No `Function1`, no `Tuple2`, no intermediate collection allocated — verified via `javap`.

### Allocation-free option type: `Nullable[A]`
Opaque union type (`A | NestedNone`) that stores values directly without wrapping (unlike `Option`/`Some`). Nested `None` values are cached for depths 0–9.

### Lazy evaluation: `Eval[A]` and `Resource[A]`
Stack-safe monadic lazy evaluation and resource management with reverse-order cleanup.

## Package Structure

- **`lowlevel`** — `Nullable`, `MkArray`, `ArrayView`
- **`lowlevel.util`** — collections, `Eval`, `Resource`, `Sort`, `Select`
- **`lowlevel.math`** — `MathUtils` (fast math from libGDX)

## Usage

```scala
// build.sbt
libraryDependencies += "com.kubuszok" %%% "lls" % "<version>"
```

```scala
import lowlevel.*
import lowlevel.util.*

// DynamicArray with unboxed int[] backing
val arr = DynamicArray[Int]()
arr.add(1, 2, 3)
arr.foreach(println) // inline, zero lambda allocation

// ObjectMap with Fibonacci hashing
val map = ObjectMap[String, Int]()
map.put("hello", 42)
map.foreachEntry((k, v) => println(s"$k=$v")) // inline iteration

// Zero-allocation array iteration
val data = Array(1, 2, 3, 4, 5)
for {
  (v, i) <- data.leanView.zipWithIndex
  if v > 2
} println(s"$i: $v")

// Nullable — no allocation for non-null values
val maybe: Nullable[String] = Nullable("hello")
maybe.fold("empty")(_.toUpperCase) // "HELLO"
```

## Building

```bash
sbt compile          # all platforms
sbt test             # all platforms
sbt 'lls-bench/Jmh/run'  # JMH benchmarks (JVM only)
```

## License

Apache License 2.0 — see [LICENSE](LICENSE).

Includes code ported from [libGDX](https://github.com/libgdx/libgdx) (Apache 2.0, original authors: Nathan Sweet, Tommy Ettinger, Jon Renner).
