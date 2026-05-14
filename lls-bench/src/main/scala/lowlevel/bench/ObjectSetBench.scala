package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.ObjectSet
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class ObjectSetBench {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var keys:   Array[String]     = uninitialized
  private var set:    ObjectSet[String] = uninitialized
  private var intSet: ObjectSet[Int]    = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = ObjectSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }

    intSet = ObjectSet[Int](size)
    i = 0
    while (i < size) { intSet.add(i); i += 1 }
  }

  // --- Add ---

  @Benchmark
  def addNew(): Boolean = set.add(s"key$size")

  @Benchmark
  def addExisting(): Boolean = set.add(keys(size / 2))

  // --- Contains ---

  @Benchmark
  def containsHit(): Boolean = set.contains(keys(size / 2))

  @Benchmark
  def containsMiss(): Boolean = set.contains("missing")

  @Benchmark
  def containsIntHit(): Boolean = intSet.contains(size / 2)

  @Benchmark
  def containsIntMiss(): Boolean = intSet.contains(-1)

  // --- Remove ---

  @Benchmark
  def removeHit(): Boolean = set.remove(keys(size / 2))

  @Benchmark
  def removeMiss(): Boolean = set.remove("missing")

  // --- Iteration ---

  @Benchmark
  def foreachAll(): Int = {
    var sum = 0
    set.foreach(k => sum += k.length)
    sum
  }

  // --- Bulk ---

  @Benchmark
  def clearAndRefill(): Unit = {
    set.clear()
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }
}
