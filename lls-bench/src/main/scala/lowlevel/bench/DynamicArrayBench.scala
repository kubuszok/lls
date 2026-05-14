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

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var intArray:    DynamicArray[Int]    = uninitialized
  private var stringArray: DynamicArray[String] = uninitialized

  @Setup(Level.Invocation)
  def setup(): Unit = {
    intArray = DynamicArray[Int](size)
    var i = 0
    while (i < size) { intArray.add(i); i += 1 }

    stringArray = DynamicArray[String](size)
    i = 0
    while (i < size) { stringArray.add(s"v$i"); i += 1 }
  }

  // --- Add ---

  @Benchmark
  def addInt(): Unit = intArray.add(42)

  @Benchmark
  def addString(): Unit = stringArray.add("hello")

  // --- Access ---

  @Benchmark
  def getInt(): Int = intArray(size / 2)

  @Benchmark
  def getString(): String = stringArray(size / 2)

  // --- Contains ---

  @Benchmark
  def containsIntHit(): Boolean = intArray.contains(size / 2)

  @Benchmark
  def containsIntMiss(): Boolean = intArray.contains(-1)

  @Benchmark
  def containsStringHit(): Boolean = stringArray.contains(s"v${size / 2}")

  @Benchmark
  def containsStringMiss(): Boolean = stringArray.contains("missing")

  // --- IndexOf ---

  @Benchmark
  def indexOfIntFirst(): Int = intArray.indexOf(0)

  @Benchmark
  def indexOfIntLast(): Int = intArray.indexOf(size - 1)

  // --- Remove ---

  @Benchmark
  def removeIndexFirst(): Int = intArray.removeIndex(0)

  @Benchmark
  def removeIndexLast(): Int = intArray.removeIndex(intArray.size - 1)

  @Benchmark
  def removeValueInt(): Boolean = intArray.removeValue(size / 2)

  // --- Iteration ---

  @Benchmark
  def foreachInt(): Int = {
    var sum = 0
    intArray.foreach(sum += _)
    sum
  }

  @Benchmark
  def foreachString(): Int = {
    var sum = 0
    stringArray.foreach(s => sum += s.length)
    sum
  }

  // --- Sort ---

  @Benchmark
  def sortInt(): Unit = intArray.sort()(using Ordering.Int.reverse)

  // --- Bulk ---

  @Benchmark
  def clearAndRefill(): Unit = {
    intArray.clear()
    var i = 0
    while (i < size) { intArray.add(i); i += 1 }
  }

  @Benchmark
  def toArray(): Array[Int] = intArray.toArray
}
