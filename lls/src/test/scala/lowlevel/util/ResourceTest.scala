package lowlevel
package util

class ResourceTest extends munit.FunSuite {

  // --- make ---

  test("make creates and cleans up resource") {
    var cleaned = false
    val value = Resource.make("hello") { _ => cleaned = true }.run()
    assertEquals(value, "hello")
    assert(cleaned)
  }

  test("make cleanup runs after use") {
    val log = scala.collection.mutable.ArrayBuffer[String]()
    val res = Resource.make({ log += "create"; 42 })(v => log += s"cleanup-$v")
    assert(log.isEmpty)
    val value = res.run()
    assertEquals(value, 42)
    assertEquals(log.toList, List("create", "cleanup-42"))
  }

  // --- pure ---

  test("pure wraps value without cleanup") {
    val (value, cleanup) = Resource.pure(42).allocate()
    assertEquals(value, 42)
    cleanup()
  }

  // --- map ---

  test("map transforms resource value") {
    val res = Resource.make("hello")(_ => ()).map(_.length)
    assertEquals(res.run(), 5)
  }

  test("map preserves cleanup") {
    var cleaned = false
    val res = Resource.make("hello")(_ => cleaned = true).map(_.length)
    assertEquals(res.run(), 5)
    assert(cleaned)
  }

  // --- flatMap ---

  test("flatMap chains resources") {
    val log = scala.collection.mutable.ArrayBuffer[String]()
    val res = Resource.make({ log += "create-a"; "hello" })(v => log += s"cleanup-a-$v")
      .flatMap { a =>
        Resource.make({ log += s"create-b-$a"; a.length })(v => log += s"cleanup-b-$v")
      }
    val value = res.run()
    assertEquals(value, 5)
    assertEquals(log.toList, List("create-a", "create-b-hello", "cleanup-b-5", "cleanup-a-hello"))
  }

  test("flatMap cleans up in reverse order") {
    val log = scala.collection.mutable.ArrayBuffer[String]()
    val res = for {
      a <- Resource.make({ log += "1"; "first" })(_ => log += "~1")
      b <- Resource.make({ log += "2"; "second" })(_ => log += "~2")
      c <- Resource.make({ log += "3"; "third" })(_ => log += "~3")
    } yield (a, b, c)
    val value = res.run()
    assertEquals(value, ("first", "second", "third"))
    assertEquals(log.toList, List("1", "2", "3", "~3", "~2", "~1"))
  }

  test("run cleans up resource after use") {
    var cleaned = false
    val value = Resource.make(42)(_ => cleaned = true).run()
    assertEquals(value, 42)
    assert(cleaned)
  }

  // --- allocate ---

  test("allocate returns value and cleanup function") {
    var cleaned = false
    val (value, cleanup) = Resource.make(42)(_ => cleaned = true).allocate()
    assertEquals(value, 42)
    assert(!cleaned)
    cleanup()
    assert(cleaned)
  }

  // --- fromCloseable ---

  test("fromCloseable creates resource from Closeable") {
    var closed = false
    val closeable = new java.io.Closeable {
      def close(): Unit = closed = true
    }
    val value = Resource.fromCloseable(closeable).run()
    assert(value eq closeable)
    assert(closed)
  }

  // --- eval ---

  test("eval exposes underlying Eval") {
    val res  = Resource.make(42)(_ => ())
    val eval = res.eval
    val (value, cleanup) = eval.run
    assertEquals(value, 42)
    cleanup.run
  }

  // --- Edge cases ---

  test("pure resource cleanup is safe no-op") {
    val res = Resource.pure("hello")
    val (value, cleanup) = res.allocate()
    assertEquals(value, "hello")
    cleanup()
    cleanup()
  }

  test("nested map and flatMap") {
    var cleaned = false
    val res = Resource.make(10)(_ => cleaned = true)
      .map(_ * 2)
      .map(_ + 1)
      .flatMap(v => Resource.pure(v.toString))
    assertEquals(res.run(), "21")
    assert(cleaned)
  }
}
