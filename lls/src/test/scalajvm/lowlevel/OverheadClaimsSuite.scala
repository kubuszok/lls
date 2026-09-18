package lowlevel

import lowlevel.util.*

/** The README's overhead claims, checked against the bytecode scalac actually produced (`javap -c`): each fixture below is one README example, and its compiled method must contain no closure
  * creation, no tuple, no boxing and no reflective array access. JVM only — there is no bytecode to read elsewhere.
  */
object OverheadFixtures {

  // README, "Zero-allocation array views": iterating a view
  def arrayViewForeach(array: Array[Int]): Int = {
    var sum = 0
    for (elem <- array.leanView) sum += elem * 10
    sum
  }

  // …and with the index. The loop body is expanded in place (no function object); the `(elem, i)`
  // pair itself is still built per element — a for-comprehension hands `foreach` a function of ONE
  // tuple argument, and the compiler does not reduce that ordinary pattern match.
  def arrayViewZipWithIndex(array: Array[Int]): Int = {
    var sum = 0
    for ((elem, i) <- array.leanView.zipWithIndex) sum += elem * 10 + i
    sum
  }

  // README, "Usage": `arr.foreach(println) // inline, zero lambda allocation`
  def dynamicArrayForeach(arr: DynamicArray[Int]): Int = {
    var sum = 0
    arr.foreach(v => sum += v)
    sum
  }

  // README, "Usage": `map.foreachEntry((k, v) => …) // inline iteration`
  def objectMapForeachEntry(map: ObjectMap[String, String]): Int = {
    var sum = 0
    map.foreachEntry((k, v) => sum += k.length + v.length)
    sum
  }

  // README, "Allocation-free option type": a value is stored directly, without a wrapper
  def nullableWrap(value: String): Nullable[String] = Nullable(value)
}

class OverheadClaimsSuite extends munit.FunSuite {

  /** `javap -c -p` of the fixtures' class, split into one text per method name. */
  private lazy val methods: Map[String, String] = {
    val javap    = java.util.spi.ToolProvider.findFirst("javap").orElseThrow(() => new AssertionError("no javap in this JDK (jdk.jdeps is missing)"))
    val location = Class.forName("lowlevel.OverheadFixtures$").getProtectionDomain.getCodeSource.getLocation
    val out      = new java.io.StringWriter
    val err      = new java.io.StringWriter
    val code     = javap.run(
      new java.io.PrintWriter(out),
      new java.io.PrintWriter(err),
      "-c",
      "-p",
      "-classpath",
      java.nio.file.Paths.get(location.toURI).toString,
      "lowlevel.OverheadFixtures$"
    )
    assertEquals(code, 0, err.toString)
    // a method's section opens with its signature line (two spaces of indent, ends with `;`)
    // (`$` is part of the name: a lambda's synthetic method must open its own section)
    val Signature = """^  \S.*\s([\w$]+)\(.*\);$""".r
    val sections  = scala.collection.mutable.LinkedHashMap.empty[String, StringBuilder]
    var current   = Option.empty[StringBuilder]
    out.toString.linesIterator.foreach {
      case line @ Signature(name) =>
        val sb = sections.getOrElseUpdate(name, new StringBuilder)
        sb.append(line).append('\n')
        current = Some(sb)
      case line => current.foreach(_.append(line).append('\n'))
    }
    sections.view.mapValues(_.toString).toMap
  }

  private val forbidden = List(
    "invokedynamic" -> "a closure is created",
    "scala/Function" -> "a function object is involved",
    "scala/Tuple2" -> "a tuple is allocated",
    "scala/runtime/BoxesRunTime.boxTo" -> "a primitive is boxed",
    "scala/runtime/ScalaRunTime$.array_" -> "the array is accessed reflectively"
  )

  private def check(method: String)(using munit.Location): Unit = {
    val body = methods.getOrElse(method, fail(s"no method `$method` in the javap output; found ${methods.keys.mkString(", ")}"))
    val hits = forbidden.collect { case (needle, meaning) if body.contains(needle) => s"$meaning ($needle)" }
    assert(hits.isEmpty, s"`$method`: ${hits.mkString("; ")}\n$body")
  }

  test("array view for-comprehension: no closure, no tuple, no boxing")(check("arrayViewForeach"))

  test("array view for-comprehension with zipWithIndex: no closure") {
    val body = methods.getOrElse("arrayViewZipWithIndex", fail("no method `arrayViewZipWithIndex` in the javap output"))
    assert(!body.contains("invokedynamic") && !body.contains("scala/Function"), body)
  }

  test("DynamicArray.foreach: no closure, no boxing, no reflective array access")(check("dynamicArrayForeach"))

  test("ObjectMap.foreachEntry: no closure, no tuple")(check("objectMapForeachEntry"))

  test("Nullable(value): the value itself, no wrapper object") {
    val body = methods.getOrElse("nullableWrap", fail("no method `nullableWrap` in the javap output"))
    assert(!body.contains(" new "), body)
    check("nullableWrap")
  }
}
