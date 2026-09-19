package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.ArrayMap
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class ArrayMapBench {

  @Benchmark
  def putNew(s: ArrayMapWriteState): Int = s.map.put(s"key${s.size}", s.size)

  @Benchmark
  def putExisting(s: ArrayMapWriteState): Int = s.map.put(s.keys(s.size / 2), 999)

  @Benchmark
  def getHit(s: ArrayMapReadState): Nullable[Int] = s.map.get(s.keys(s.size / 2))

  @Benchmark
  def getMiss(s: ArrayMapReadState): Nullable[Int] = s.map.get("missing")

  @Benchmark
  def containsKeyHit(s: ArrayMapReadState): Boolean = s.map.containsKey(s.keys(s.size / 2))

  @Benchmark
  def containsKeyMiss(s: ArrayMapReadState): Boolean = s.map.containsKey("missing")

  @Benchmark
  def removeKey(s: ArrayMapWriteState): Nullable[Int] = s.map.removeKey(s.keys(s.size / 2))

  @Benchmark
  def foreachEntry(s: ArrayMapReadState): Int = {
    var sum = 0
    s.map.foreachEntry((_, v) => sum += v)
    sum
  }

  @Benchmark
  def clearAndRefill(s: ArrayMapWriteState): Unit = {
    s.map.clear()
    var i = 0
    while (i < s.size) { s.map.put(s.keys(i), i); i += 1 }
  }
}

// Read-only benchmarks (get/contains/foreach) do not modify the map, so the map is built once per
// iteration rather than before every invocation.
@State(Scope.Thread)
class ArrayMapReadState {

  @Param(Array("100", "1000"))
  var size: Int = uninitialized

  var keys: Array[String]         = uninitialized
  var map:  ArrayMap[String, Int] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ArrayMap[String, Int](size)
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }
}

// Mutating benchmarks (put/remove/clear) need a fresh map before every invocation.
@State(Scope.Thread)
class ArrayMapWriteState {

  @Param(Array("100", "1000"))
  var size: Int = uninitialized

  var keys: Array[String]         = uninitialized
  var map:  ArrayMap[String, Int] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ArrayMap[String, Int](size)
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }
}
