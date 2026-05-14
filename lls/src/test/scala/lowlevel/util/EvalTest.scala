package lowlevel
package util

class EvalTest extends munit.FunSuite {

  // --- Construction ---

  test("pure wraps value") {
    assertEquals(Eval.pure(42).run, 42)
  }

  test("apply defers evaluation") {
    var evaluated = false
    val e = Eval { evaluated = true; 42 }
    assert(!evaluated)
    assertEquals(e.run, 42)
    assert(evaluated)
  }

  test("defer defers thunk") {
    var evaluated = false
    val e = Eval.defer { evaluated = true; Eval.pure(42) }
    assert(!evaluated)
    assertEquals(e.run, 42)
    assert(evaluated)
  }

  test("void produces unit") {
    assertEquals(Eval.void.run, ())
  }

  // --- map ---

  test("map transforms value") {
    assertEquals(Eval.pure(10).map(_ * 2).run, 20)
  }

  test("map chains") {
    assertEquals(Eval.pure("hello").map(_.length).map(_ + 1).run, 6)
  }

  test("map defers computation") {
    var count = 0
    val e = Eval.pure(1).map { v => count += 1; v + 1 }
    assertEquals(count, 0)
    assertEquals(e.run, 2)
    assertEquals(count, 1)
  }

  // --- flatMap ---

  test("flatMap chains computations") {
    val result = Eval.pure(10).flatMap(a => Eval.pure(a + 5)).run
    assertEquals(result, 15)
  }

  test("flatMap defers") {
    var count = 0
    val e = Eval.pure(1).flatMap { v => count += 1; Eval.pure(v + 1) }
    assertEquals(count, 0)
    assertEquals(e.run, 2)
    assertEquals(count, 1)
  }

  test("deep flatMap chain is stack-safe") {
    val n = 100000
    var e = Eval.pure(0)
    for (_ <- 0 until n)
      e = e.flatMap(v => Eval.pure(v + 1))
    assertEquals(e.run, n)
  }

  // --- flatten ---

  test("flatten unwraps nested Eval") {
    val nested: Eval[Eval[Int]] = Eval.pure(Eval.pure(42))
    assertEquals(nested.flatten.run, 42)
  }

  // --- flatTap ---

  test("flatTap runs side effect and returns original value") {
    var sideEffect = 0
    val result = Eval.pure(42).flatTap(v => Eval { sideEffect = v; () }).run
    assertEquals(result, 42)
    assertEquals(sideEffect, 42)
  }

  // --- mapTap ---

  test("mapTap runs function and returns original value") {
    var sideEffect = 0
    val result = Eval.pure(42).mapTap(v => sideEffect = v).run
    assertEquals(result, 42)
    assertEquals(sideEffect, 42)
  }

  // --- map2 / tuple ---

  test("map2 combines two Evals") {
    val result = Eval.pure(10).map2(Eval.pure(20))(_ + _).run
    assertEquals(result, 30)
  }

  test("tuple pairs two Evals") {
    assertEquals(Eval.pure(1).tuple(Eval.pure("a")).run, (1, "a"))
  }

  // --- as / void ---

  test("as replaces value") {
    assertEquals(Eval.pure(42).as("hello").run, "hello")
  }

  test("void discards value") {
    assertEquals(Eval.pure(42).void.run, ())
  }

  // --- >> / <* / *> ---

  test(">> sequences and keeps second") {
    var first = false
    val result = Eval { first = true; 1 } >> Eval.pure(2)
    assertEquals(result.run, 2)
    assert(first)
  }

  test("<* sequences and keeps first") {
    assertEquals((Eval.pure(1) <* Eval.pure(2)).run, 1)
  }

  test("*> sequences and keeps second") {
    assertEquals((Eval.pure(1) *> Eval.pure(2)).run, 2)
  }

  // --- Deferred side effects ---

  test("side effects execute in order") {
    val log = scala.collection.mutable.ArrayBuffer[String]()
    val e = for {
      _ <- Eval { log += "first"; () }
      _ <- Eval { log += "second"; () }
      _ <- Eval { log += "third"; () }
    } yield ()
    assert(log.isEmpty)
    e.run
    assertEquals(log.toList, List("first", "second", "third"))
  }

  test("Eval.apply re-evaluates each run") {
    var count = 0
    val e = Eval { count += 1; count }
    assertEquals(e.run, 1)
    assertEquals(e.run, 2)
    assertEquals(e.run, 3)
  }
}
