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

  @Param(Array("100", "1000"))
  var size: Int = uninitialized

  private var keys: Array[String]          = uninitialized
  private var map:  ArrayMap[String, Int]   = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ArrayMap[String, Int](size)
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }

  @Benchmark
  def putNew(): Int = map.put(s"key$size", size)

  @Benchmark
  def putExisting(): Int = map.put(keys(size / 2), 999)

  @Benchmark
  def getHit(): Nullable[Int] = map.get(keys(size / 2))

  @Benchmark
  def getMiss(): Nullable[Int] = map.get("missing")

  @Benchmark
  def containsKeyHit(): Boolean = map.containsKey(keys(size / 2))

  @Benchmark
  def containsKeyMiss(): Boolean = map.containsKey("missing")

  @Benchmark
  def removeKey(): Nullable[Int] = map.removeKey(keys(size / 2))

  @Benchmark
  def foreachEntry(): Int = {
    var sum = 0
    map.foreachEntry((_, v) => sum += v)
    sum
  }

  @Benchmark
  def clearAndRefill(): Unit = {
    map.clear()
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }
}
