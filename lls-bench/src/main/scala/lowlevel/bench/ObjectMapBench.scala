package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.ObjectMap
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class ObjectMapBench {

  @Benchmark
  def putNew(s: ObjectMapWriteState): Nullable[java.lang.Integer] =
    s.map.put(s"key${s.nextKey}", Nullable(s.nextKey: java.lang.Integer))

  @Benchmark
  def putExisting(s: ObjectMapWriteState): Nullable[java.lang.Integer] =
    s.map.put(s.keys(s.size / 2), Nullable(999: java.lang.Integer))

  @Benchmark
  def putIntNew(s: ObjectMapWriteState): Nullable[java.lang.Integer] =
    s.intMap.put(s.nextKey: java.lang.Integer, Nullable((s.nextKey * 10): java.lang.Integer))

  @Benchmark
  def putIntExisting(s: ObjectMapWriteState): Nullable[java.lang.Integer] =
    s.intMap.put((s.size / 2): java.lang.Integer, Nullable(999: java.lang.Integer))

  @Benchmark
  def getHit(s: ObjectMapReadState): Nullable[java.lang.Integer] = s.map.get(s.keys(s.size / 2))

  @Benchmark
  def getMiss(s: ObjectMapReadState): Nullable[java.lang.Integer] = s.map.get("missing")

  @Benchmark
  def getIntHit(s: ObjectMapReadState): Nullable[java.lang.Integer] = s.intMap.get((s.size / 2): java.lang.Integer)

  @Benchmark
  def getIntMiss(s: ObjectMapReadState): Nullable[java.lang.Integer] = s.intMap.get(-1: java.lang.Integer)

  @Benchmark
  def getWithDefault(s: ObjectMapReadState): java.lang.Integer = s.map.get("missing", Nullable(-1: java.lang.Integer)).nn

  @Benchmark
  def containsKeyHit(s: ObjectMapReadState): Boolean = s.map.containsKey(s.keys(s.size / 2))

  @Benchmark
  def containsKeyMiss(s: ObjectMapReadState): Boolean = s.map.containsKey("missing")

  @Benchmark
  def removeHit(s: ObjectMapWriteState): Nullable[java.lang.Integer] = s.map.remove(s.keys(s.size / 2))

  // A miss returns from `ObjectMap#remove` right after `locateKey`, which only reads the table:
  // no slot is written, no size changes. Read-only, verified against the generated source.
  @Benchmark
  def removeMiss(s: ObjectMapReadState): Nullable[java.lang.Integer] = s.map.remove("missing")

  @Benchmark
  def foreachEntry(s: ObjectMapReadState): Int = {
    var sum = 0
    s.map.foreachEntry((_, v) => sum += v.nn.intValue)
    sum
  }

  @Benchmark
  def foreachKey(s: ObjectMapReadState): Int = {
    var sum = 0
    s.map.foreachKey(k => sum += k.length)
    sum
  }

  @Benchmark
  def clearAndRefill(s: ObjectMapWriteState): Unit = {
    s.map.clear()
    var i = 0
    while (i < s.size) { s.map.put(s.keys(i), Nullable(i: java.lang.Integer)); i += 1 }
  }

  // The destination map is freshly created inside the benchmark body; the shared `map` is only
  // read via `putAll`, so this can share the read-only state.
  @Benchmark
  def putAllFromCopy(s: ObjectMapReadState): Unit = {
    val dest = ObjectMap[String, java.lang.Integer](s.size)
    dest.putAll(s.map)
  }
}

// Read-only benchmarks (get/contains/foreach/putAll-as-source/miss-remove) do not modify the maps,
// so they are built once per iteration rather than before every invocation.
@State(Scope.Thread)
class ObjectMapReadState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys:   Array[String]                                   = uninitialized
  var map:    ObjectMap[String, java.lang.Integer]            = uninitialized
  var intMap: ObjectMap[java.lang.Integer, java.lang.Integer] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ObjectMap[String, java.lang.Integer](size)
    var i = 0
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }

    intMap = ObjectMap[java.lang.Integer, java.lang.Integer](size)
    i = 0
    while (i < size) { intMap.put(i: java.lang.Integer, Nullable((i * 10): java.lang.Integer)); i += 1 }
  }
}

// Mutating benchmarks (put/hit-remove/clear) need a fresh map before every invocation.
@State(Scope.Thread)
class ObjectMapWriteState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys:    Array[String]                                   = uninitialized
  var map:     ObjectMap[String, java.lang.Integer]            = uninitialized
  var intMap:  ObjectMap[java.lang.Integer, java.lang.Integer] = uninitialized
  var nextKey: Int                                             = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ObjectMap[String, java.lang.Integer](size)
    var i = 0
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }

    intMap = ObjectMap[java.lang.Integer, java.lang.Integer](size)
    i = 0
    while (i < size) { intMap.put(i: java.lang.Integer, Nullable((i * 10): java.lang.Integer)); i += 1 }

    nextKey = size
  }
}
