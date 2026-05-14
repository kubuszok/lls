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

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var keys:    Array[String]          = uninitialized
  private var map:     ObjectMap[String, Int] = uninitialized
  private var intMap:  ObjectMap[Int, Int]    = uninitialized
  private var nextKey: Int                    = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    map = ObjectMap[String, Int](size)
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }

    intMap = ObjectMap[Int, Int](size)
    i = 0
    while (i < size) { intMap.put(i, i * 10); i += 1 }

    nextKey = size
  }

  // --- Put ---

  @Benchmark
  def putNew(): Nullable[Int] = map.put(s"key$nextKey", nextKey)

  @Benchmark
  def putExisting(): Nullable[Int] = map.put(keys(size / 2), 999)

  @Benchmark
  def putIntNew(): Nullable[Int] = intMap.put(nextKey, nextKey * 10)

  @Benchmark
  def putIntExisting(): Nullable[Int] = intMap.put(size / 2, 999)

  // --- Get ---

  @Benchmark
  def getHit(): Nullable[Int] = map.get(keys(size / 2))

  @Benchmark
  def getMiss(): Nullable[Int] = map.get("missing")

  @Benchmark
  def getIntHit(): Nullable[Int] = intMap.get(size / 2)

  @Benchmark
  def getIntMiss(): Nullable[Int] = intMap.get(-1)

  @Benchmark
  def getWithDefault(): Int = map.get("missing", -1)

  // --- ContainsKey ---

  @Benchmark
  def containsKeyHit(): Boolean = map.containsKey(keys(size / 2))

  @Benchmark
  def containsKeyMiss(): Boolean = map.containsKey("missing")

  // --- Remove ---

  @Benchmark
  def removeHit(): Nullable[Int] = map.remove(keys(size / 2))

  @Benchmark
  def removeMiss(): Nullable[Int] = map.remove("missing")

  // --- Iteration ---

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

  // --- Bulk ---

  @Benchmark
  def clearAndRefill(): Unit = {
    map.clear()
    var i = 0
    while (i < size) { map.put(keys(i), i); i += 1 }
  }

  @Benchmark
  def putAllFromCopy(): Unit = {
    val dest = ObjectMap[String, Int](size)
    dest.putAll(map)
  }
}
