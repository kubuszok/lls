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

  @Benchmark
  def addNew(s: OrderedSetWriteState): Boolean = s.set.add(s"key${s.size}")

  @Benchmark
  def addExisting(s: OrderedSetWriteState): Boolean = s.set.add(s.keys(s.size / 2))

  @Benchmark
  def containsHit(s: OrderedSetReadState): Boolean = s.set.contains(s.keys(s.size / 2))

  @Benchmark
  def containsMiss(s: OrderedSetReadState): Boolean = s.set.contains("missing")

  @Benchmark
  def removeHit(s: OrderedSetWriteState): Boolean = s.set.remove(s.keys(s.size / 2))

  @Benchmark
  def foreachAll(s: OrderedSetReadState): Int = {
    var sum = 0
    s.set.foreach(k => sum += k.length)
    sum
  }

  @Benchmark
  def clearAndRefill(s: OrderedSetWriteState): Unit = {
    s.set.clear()
    var i = 0
    while (i < s.size) { s.set.add(s.keys(i)); i += 1 }
  }
}

// Read-only benchmarks (contains/foreach) do not modify the set, so it is built once per
// iteration rather than before every invocation.
@State(Scope.Thread)
class OrderedSetReadState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys: Array[String]      = uninitialized
  var set:  OrderedSet[String] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = OrderedSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }
}

// Mutating benchmarks (add/remove/clear) need a fresh set before every invocation.
@State(Scope.Thread)
class OrderedSetWriteState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys: Array[String]      = uninitialized
  var set:  OrderedSet[String] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = OrderedSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }
}
