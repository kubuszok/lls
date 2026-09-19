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

  // --- Add ---

  @Benchmark
  def addNew(s: ObjectSetWriteState): Boolean = s.set.add(s"key${s.size}")

  @Benchmark
  def addExisting(s: ObjectSetWriteState): Boolean = s.set.add(s.keys(s.size / 2))

  // --- Contains ---

  @Benchmark
  def containsHit(s: ObjectSetReadState): Boolean = s.set.contains(s.keys(s.size / 2))

  @Benchmark
  def containsMiss(s: ObjectSetReadState): Boolean = s.set.contains("missing")

  @Benchmark
  def containsIntHit(s: ObjectSetReadState): Boolean = s.intSet.contains(s.size / 2)

  @Benchmark
  def containsIntMiss(s: ObjectSetReadState): Boolean = s.intSet.contains(-1)

  // --- Remove ---

  @Benchmark
  def removeHit(s: ObjectSetWriteState): Boolean = s.set.remove(s.keys(s.size / 2))

  // A miss returns from `ObjectSet#remove` right after `locateKey`, which only reads the table:
  // no slot is written, no size changes. Read-only, verified against the generated source.
  @Benchmark
  def removeMiss(s: ObjectSetReadState): Boolean = s.set.remove("missing")

  // --- Iteration ---

  @Benchmark
  def foreachAll(s: ObjectSetReadState): Int = {
    var sum = 0
    s.set.foreach(k => sum += k.length)
    sum
  }

  // --- Bulk ---

  @Benchmark
  def clearAndRefill(s: ObjectSetWriteState): Unit = {
    s.set.clear()
    var i = 0
    while (i < s.size) { s.set.add(s.keys(i)); i += 1 }
  }
}

// Read-only benchmarks (contains/foreach/miss-remove) do not modify the sets, so they are built
// once per iteration rather than before every invocation.
@State(Scope.Thread)
class ObjectSetReadState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys:   Array[String]                = uninitialized
  var set:    ObjectSet[String]            = uninitialized
  var intSet: ObjectSet[java.lang.Integer] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = ObjectSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }

    intSet = ObjectSet[java.lang.Integer](size)
    i = 0
    while (i < size) { intSet.add(i); i += 1 }
  }
}

// Mutating benchmarks (add/hit-remove/clear) need a fresh set before every invocation.
@State(Scope.Thread)
class ObjectSetWriteState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var keys: Array[String]     = uninitialized
  var set:  ObjectSet[String] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    keys = Array.tabulate(size)(i => s"key$i")
    set = ObjectSet[String](size)
    var i = 0
    while (i < size) { set.add(keys(i)); i += 1 }
  }
}
