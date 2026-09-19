package lowlevel
package bench

import scala.compiletime.uninitialized
import lowlevel.util.DynamicArray
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class DynamicArrayBench {

  // --- Add ---

  @Benchmark
  def addInt(s: DynamicArrayWriteState): Unit = s.intArray.add(42)

  @Benchmark
  def addString(s: DynamicArrayWriteState): Unit = s.stringArray.add("hello")

  // --- Access ---

  @Benchmark
  def getInt(s: DynamicArrayReadState): Int = s.intArray(s.size / 2)

  @Benchmark
  def getString(s: DynamicArrayReadState): String = s.stringArray(s.size / 2)

  // --- Contains ---

  @Benchmark
  def containsIntHit(s: DynamicArrayReadState): Boolean = s.intArray.contains(s.size / 2)

  @Benchmark
  def containsIntMiss(s: DynamicArrayReadState): Boolean = s.intArray.contains(-1)

  @Benchmark
  def containsStringHit(s: DynamicArrayReadState): Boolean = s.stringArray.contains(s"v${s.size / 2}")

  @Benchmark
  def containsStringMiss(s: DynamicArrayReadState): Boolean = s.stringArray.contains("missing")

  // --- IndexOf ---

  @Benchmark
  def indexOfIntFirst(s: DynamicArrayReadState): Int = s.intArray.indexOf(0)

  @Benchmark
  def indexOfIntLast(s: DynamicArrayReadState): Int = s.intArray.indexOf(s.size - 1)

  // --- Remove ---

  @Benchmark
  def removeIndexFirst(s: DynamicArrayWriteState): Int = s.intArray.removeIndex(0)

  @Benchmark
  def removeIndexLast(s: DynamicArrayWriteState): Int = s.intArray.removeIndex(s.intArray.size - 1)

  @Benchmark
  def removeValueInt(s: DynamicArrayWriteState): Boolean = s.intArray.removeValue(s.size / 2)

  // --- Iteration ---

  @Benchmark
  def foreachInt(s: DynamicArrayReadState): Int = {
    var sum = 0
    s.intArray.foreach(sum += _)
    sum
  }

  @Benchmark
  def foreachString(s: DynamicArrayReadState): Int = {
    var sum = 0
    s.stringArray.foreach(str => sum += str.length)
    sum
  }

  // --- Sort ---

  @Benchmark
  def sortInt(s: DynamicArrayWriteState): Unit = s.intArray.sort()

  // --- Bulk ---

  @Benchmark
  def clearAndRefill(s: DynamicArrayWriteState): Unit = {
    s.intArray.clear()
    var i = 0
    while (i < s.size) { s.intArray.add(i); i += 1 }
  }

  @Benchmark
  def toArray(s: DynamicArrayReadState): Array[Int] = s.intArray.toArray()
}

// Read-only benchmarks (get/contains/indexOf/foreach/toArray) do not modify the array, so it is
// built once per iteration rather than before every invocation.
@State(Scope.Thread)
class DynamicArrayReadState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var intArray:    DynamicArray[Int]    = uninitialized
  var stringArray: DynamicArray[String] = uninitialized

  @Setup(Level.Iteration)
  def setup(): Unit = {
    intArray = DynamicArray[Int](size)
    var i = 0
    while (i < size) { intArray.add(i); i += 1 }

    stringArray = DynamicArray[String](size)
    i = 0
    while (i < size) { stringArray.add(s"v$i"); i += 1 }
  }
}

// Mutating benchmarks (add/remove/sort/clear) need a fresh array before every invocation.
@State(Scope.Thread)
class DynamicArrayWriteState {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  var intArray:    DynamicArray[Int]    = uninitialized
  var stringArray: DynamicArray[String] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    intArray = DynamicArray[Int](size)
    var i = 0
    while (i < size) { intArray.add(i); i += 1 }

    stringArray = DynamicArray[String](size)
    i = 0
    while (i < size) { stringArray.add(s"v$i"); i += 1 }
  }
}
