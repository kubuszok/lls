package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.OrderedSet
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class OrderedSetBench {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var keys: Array[String]      = uninitialized
  private var set:  OrderedSet[String] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = OrderedSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }

  @Benchmark
  def addNew(): Boolean = set.add(s"key$size")

  @Benchmark
  def addExisting(): Boolean = set.add(keys(size / 2))

  @Benchmark
  def containsHit(): Boolean = set.contains(keys(size / 2))

  @Benchmark
  def containsMiss(): Boolean = set.contains("missing")

  @Benchmark
  def removeHit(): Boolean = set.remove(keys(size / 2))

  @Benchmark
  def foreachAll(): Int = {
    var sum = 0
    set.foreach(k => sum += k.length)
    sum
  }

  @Benchmark
  def clearAndRefill(): Unit = {
    set.clear()
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }
}
