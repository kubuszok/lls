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

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var keys:    Array[String]           = uninitialized
  private var map:     OrderedMap[String, Int] = uninitialized
  private var nextKey: Int                     = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = OrderedMap[String, Int](size)
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
    nextKey = size
  }

  @Benchmark
  def putNew(): Nullable[Int] = map.put(s"key$nextKey", nextKey)

  @Benchmark
  def putExisting(): Nullable[Int] = map.put(keys(size / 2), 999)

  @Benchmark
  def getHit(): Nullable[Int] = map.get(keys(size / 2))

  @Benchmark
  def getMiss(): Nullable[Int] = map.get("missing")

  @Benchmark
  def removeHit(): Nullable[Int] = map.remove(keys(size / 2))

  @Benchmark
  def foreachEntry(): Int = {
    var sum = 0
    map.foreachEntry((_, v) => sum += v)
    sum
  }

  @Benchmark
  def foreachKey(): Int = {
    var sum = 0
    map.foreachKey(k => sum += k.length)
    sum
  }

  @Benchmark
  def clearAndRefill(): Unit = {
    map.clear()
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }
}
