package lowlevel
package bench

import scala.compiletime.uninitialized
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
class MkArrayBench {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var intArray:    Array[Int]    = uninitialized
  private var stringArray: Array[String] = uninitialized

  @Setup
  def setup(): Unit = {
    intArray = Array.tabulate(size)(identity)
    stringArray = Array.tabulate(size)(i => s"v$i")
  }

  @Benchmark
  def createIntArray(): Array[Int] = MkArray.mkInt.create(size)

  @Benchmark
  def createStringArray(): Array[String] = MkArray.anyRef[String].create(size)

  @Benchmark
  def copyOfInt(): Array[Int] = MkArray.mkInt.copyOf(intArray, size * 2)

  @Benchmark
  def copyOfString(): Array[String] = MkArray.anyRef[String].copyOf(stringArray, size * 2)

  @Benchmark
  def copyOfRangeInt(): Array[Int] = MkArray.mkInt.copyOfRange(intArray, size / 4, size * 3 / 4)

  @Benchmark
  def copyOfRangeString(): Array[String] = MkArray.anyRef[String].copyOfRange(stringArray, size / 4, size * 3 / 4)
}
