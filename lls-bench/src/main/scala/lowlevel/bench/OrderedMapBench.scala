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

  private var keys:    Array[String]                         = uninitialized
  private var map:     OrderedMap[String, java.lang.Integer] = uninitialized
  private var nextKey: Int                                   = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = OrderedMap[String, java.lang.Integer](size)
    var i = 0
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }
    nextKey = size
  }

  @Benchmark
  def putNew(): Nullable[java.lang.Integer] = map.put(s"key$nextKey", Nullable(nextKey: java.lang.Integer))

  @Benchmark
  def putExisting(): Nullable[java.lang.Integer] = map.put(keys(size / 2), Nullable(999: java.lang.Integer))

  @Benchmark
  def getHit(): Nullable[java.lang.Integer] = map.get(keys(size / 2))

  @Benchmark
  def getMiss(): Nullable[java.lang.Integer] = map.get("missing")

  @Benchmark
  def removeHit(): Nullable[java.lang.Integer] = map.remove(keys(size / 2))

  @Benchmark
  def foreachEntry(): Int = {
    var sum = 0
    map.foreachEntry((_, v) => sum += v.nn.intValue)
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
    while (i < size) { map.put(keys(i), Nullable(i: java.lang.Integer)); i += 1 }
  }
}
