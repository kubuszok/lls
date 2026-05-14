package lowlevel
package bench

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import scala.compiletime.uninitialized

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class ArrayViewBench {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var intArray:    Array[Int]    = uninitialized
  private var stringArray: Array[String] = uninitialized

  @Setup
  def setup(): Unit = {
    intArray = Array.tabulate(size)(identity)
    stringArray = Array.tabulate(size)(i => s"v$i")
  }

  // --- foreach: leanView vs stdlib ---

  @Benchmark
  def foreachIntLeanView(): Int = {
    var sum = 0
    intArray.leanView.foreach(sum += _)
    sum
  }

  @Benchmark
  def foreachIntStdlib(): Int = {
    var sum = 0
    intArray.foreach(sum += _)
    sum
  }

  @Benchmark
  def foreachIntWhileLoop(): Int = {
    var sum = 0
    var i   = 0
    while (i < intArray.length) { sum += intArray(i); i += 1 }
    sum
  }

  @Benchmark
  def foreachStringLeanView(): Int = {
    var sum = 0
    stringArray.leanView.foreach(s => sum += s.length)
    sum
  }

  @Benchmark
  def foreachStringStdlib(): Int = {
    var sum = 0
    stringArray.foreach(s => sum += s.length)
    sum
  }

  // --- map: leanView vs stdlib ---

  @Benchmark
  def mapIntLeanView(): Array[Int] =
    intArray.leanView.map(_ * 2)

  @Benchmark
  def mapIntStdlib(): Array[Int] =
    intArray.map(_ * 2)

  @Benchmark
  def mapStringToIntLeanView(): Array[Int] =
    stringArray.leanView.map(_.length)

  @Benchmark
  def mapStringToIntStdlib(): Array[Int] =
    stringArray.map(_.length)

  // --- filter + foreach: leanView vs stdlib ---

  @Benchmark
  def filterForeachIntLeanView(): Int = {
    var sum = 0
    for {
      x <- intArray.leanView
      if x % 2 == 0
    } sum += x
    sum
  }

  @Benchmark
  def filterForeachIntStdlib(): Int = {
    var sum = 0
    for {
      x <- intArray
      if x % 2 == 0
    } sum += x
    sum
  }

  // --- filter + map (yield): leanView vs stdlib ---

  @Benchmark
  def filterMapIntLeanView(): Array[Int] =
    for {
      x <- intArray.leanView
      if x % 3 == 0
    } yield x * 10

  @Benchmark
  def filterMapIntStdlib(): Array[Int] =
    for {
      x <- intArray
      if x % 3 == 0
    } yield x * 10

  // --- zipWithIndex + foreach: leanView vs stdlib ---

  @Benchmark
  def zipWithIndexForeachLeanView(): Int = {
    var sum = 0
    for ((v, i) <- intArray.leanView.zipWithIndex) sum += v + i
    sum
  }

  @Benchmark
  def zipWithIndexForeachStdlib(): Int = {
    var sum = 0
    for ((v, i) <- intArray.zipWithIndex) sum += v + i
    sum
  }

  // --- nested for: leanView vs stdlib ---

  @Benchmark
  def nestedForeachLeanView(): Int = {
    val a1  = intArray
    val a2  = Array(1, 2, 3)
    var sum = 0
    for {
      x <- a1.leanView
      y <- a2.leanView
    } sum += x + y
    sum
  }

  @Benchmark
  def nestedForeachStdlib(): Int = {
    val a1  = intArray
    val a2  = Array(1, 2, 3)
    var sum = 0
    for {
      x <- a1
      y <- a2
    } sum += x + y
    sum
  }

  // --- flatMap: leanView vs stdlib ---

  @Benchmark
  def flatMapLeanView(): Array[Int] =
    intArray.leanView.flatMap(x => Array(x, x * 10))

  @Benchmark
  def flatMapStdlib(): Array[Int] =
    intArray.flatMap(x => Array(x, x * 10))
}
