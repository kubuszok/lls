package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.OrderedMap
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class OrderedMapBench {

  @Benchmark
  def putNew(s: OrderedMapWriteState): Nullable[java.lang.Integer] =
    s.map.put(s"key${s.nextKey}", Nullable(s.nextKey: java.lang.Integer))

  @Benchmark
  def putExisting(s: OrderedMapWriteState): Nullable[java.lang.Integer] =
    s.map.put(s.keys(s.size / 2), Nullable(999: java.lang.Integer))

  @Benchmark
  def getHit(s: OrderedMapReadState): Nullable[java.lang.Integer] = s.map.get(s.keys(s.size / 2))

  @Benchmark
  def getMiss(s: OrderedMapReadState): Nullable[java.lang.Integer] = s.map.get("missing")

  @Benchmark
  def removeHit(s: OrderedMapWriteState): Nullable[java.lang.Integer] = s.map.remove(s.keys(s.size / 2))

  @Benchmark
  def foreachEntry(s: OrderedMapReadState): Int = {
    var sum = 0
    s.map.foreachEntry((_, v) => sum += v.nn.intValue)
    sum
  }

  @Benchmark
  def foreachKey(s: OrderedMapReadState): Int = {
    var sum = 0
    s.map.foreachKey(k => sum += k.length)
    sum
  }

  @Benchmark
  def clearAndRefill(s: OrderedMapWriteState): Unit = {
    s.map.clear()
    var i = 0
    while (i < s.size) { s.map.put(s.keys(i), Nullable(i: java.lang.Integer)); i += 1 }
  }
}

// Read-only benchmarks (get/foreach) do not modify the map, so it is built once per iteration
// rather than before every invocation.
@State(Scope.Thread)
class OrderedMapReadState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys: Array[String]                         = uninitialized
  var map:  OrderedMap[String, java.lang.Integer] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = OrderedMap[String, java.lang.Integer](size)
    var i = 0
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }
  }
}

// Mutating benchmarks (put/remove/clear) need a fresh map before every invocation.
@State(Scope.Thread)
class OrderedMapWriteState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys:    Array[String]                         = uninitialized
  var map:     OrderedMap[String, java.lang.Integer] = uninitialized
  var nextKey: Int                                   = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = OrderedMap[String, java.lang.Integer](size)
    var i = 0
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }
    nextKey = size
  }
}
