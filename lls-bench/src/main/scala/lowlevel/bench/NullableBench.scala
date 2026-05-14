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
class NullableBench {

  @Param(Array("100", "10000"))
  var size: Int = uninitialized

  private var values: Array[Nullable[String]] = uninitialized

  @Setup
  def setup(): Unit = {
    values = new Array[Nullable[String]](size)
    var i = 0
    while (i < size) {
      values(i) = if (i % 10 == 0) Nullable.empty[String] else Nullable(s"v$i")
      i += 1
    }
  }

  @Benchmark
  def createNonEmpty(): Nullable[String] = Nullable("hello")

  @Benchmark
  def createEmpty(): Nullable[String] = Nullable.empty[String]

  @Benchmark
  def mapNonEmpty(): Nullable[Int] = Nullable("hello").map(_.length)

  @Benchmark
  def mapEmpty(): Nullable[Int] = Nullable.empty[String].map(_.length)

  @Benchmark
  def foldAll(): Int = {
    var sum = 0
    var i   = 0
    while (i < values.length) {
      sum += values(i).fold(0)(_.length)
      i += 1
    }
    sum
  }

  @Benchmark
  def getOrElseAll(): Int = {
    var sum = 0
    var i   = 0
    while (i < values.length) {
      sum += values(i).getOrElse("default").length
      i += 1
    }
    sum
  }

  @Benchmark
  def isDefinedAll(): Int = {
    var count = 0
    var i     = 0
    while (i < values.length) {
      if (values(i).isDefined) count += 1
      i += 1
    }
    count
  }
}
