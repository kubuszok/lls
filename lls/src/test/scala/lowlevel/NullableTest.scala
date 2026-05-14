package lowlevel

class NullableTest extends munit.FunSuite {

  // --- Construction ---

  test("apply wraps non-null value") {
    val n = Nullable("hello")
    assert(n.isDefined)
    assertEquals(n.get, "hello")
  }

  test("apply wraps null as empty") {
    val n = Nullable(null: String)
    assert(n.isEmpty)
  }

  test("empty creates empty Nullable") {
    val n = Nullable.empty[String]
    assert(n.isEmpty)
    assert(!n.isDefined)
  }

  test("fromOption Some") {
    val n = Nullable.fromOption(Some(42))
    assert(n.isDefined)
    assertEquals(n.get, 42)
  }

  test("fromOption None") {
    val n = Nullable.fromOption(Option.empty[Int])
    assert(n.isEmpty)
  }

  // --- map / flatMap / flatten ---

  test("map on non-empty") {
    val n = Nullable("hello")
    val mapped = n.map(_.length)
    assert(mapped.isDefined)
    assertEquals(mapped.get, 5)
  }

  test("map on empty") {
    val n = Nullable.empty[String]
    val mapped = n.map(_.length)
    assert(mapped.isEmpty)
  }

  test("flatMap on non-empty") {
    val n = Nullable("hello")
    val result = n.flatMap(s => Nullable(s.length))
    assertEquals(result.get, 5)
  }

  test("flatMap on non-empty to empty") {
    val n = Nullable("hello")
    val result = n.flatMap(_ => Nullable.empty[Int])
    assert(result.isEmpty)
  }

  test("flatMap on empty") {
    val n = Nullable.empty[String]
    val result = n.flatMap(s => Nullable(s.length))
    assert(result.isEmpty)
  }

  test("flatten non-empty of non-empty") {
    val n: Nullable[Nullable[String]] = Nullable(Nullable("hello"))
    assertEquals(n.flatten.get, "hello")
  }

  test("flatten non-empty of empty") {
    val inner: Nullable[String] = Nullable.empty[String]
    val n: Nullable[Nullable[String]] = Nullable(inner)
    assert(n.flatten.isEmpty)
  }

  test("flatten empty") {
    val n: Nullable[Nullable[String]] = Nullable.empty
    assert(n.flatten.isEmpty)
  }

  // --- foreach / fold / getOrElse ---

  test("foreach on non-empty calls function") {
    var called = false
    Nullable("hello").foreach(_ => called = true)
    assert(called)
  }

  test("foreach on empty does not call function") {
    var called = false
    Nullable.empty[String].foreach(_ => called = true)
    assert(!called)
  }

  test("fold on non-empty uses onSome") {
    assertEquals(Nullable("hello").fold(0)(_.length), 5)
  }

  test("fold on empty uses onEmpty") {
    assertEquals(Nullable.empty[String].fold(0)(_.length), 0)
  }

  test("getOrElse on non-empty returns value") {
    assertEquals(Nullable("hello").getOrElse("default"), "hello")
  }

  test("getOrElse on empty returns default") {
    assertEquals(Nullable.empty[String].getOrElse("default"), "default")
  }

  // --- get ---

  test("get on non-empty returns value") {
    assertEquals(Nullable(42).get, 42)
  }

  test("get on empty throws NullPointerException") {
    intercept[NullPointerException] {
      Nullable.empty[Int].get
    }
  }

  // --- isDefined / isEmpty ---

  test("isDefined true for non-empty") {
    assert(Nullable("x").isDefined)
  }

  test("isDefined false for empty") {
    assert(!Nullable.empty[String].isDefined)
  }

  test("isEmpty true for empty") {
    assert(Nullable.empty[String].isEmpty)
  }

  test("isEmpty false for non-empty") {
    assert(!Nullable("x").isEmpty)
  }

  // --- orElse ---

  test("orElse on non-empty returns self") {
    val n = Nullable("first")
    assertEquals(n.orElse(Nullable("second")).get, "first")
  }

  test("orElse on empty returns alternative") {
    val n = Nullable.empty[String]
    assertEquals(n.orElse(Nullable("second")).get, "second")
  }

  // --- exists / forall / contains / filter ---

  test("exists true when predicate matches") {
    assert(Nullable(5).exists(_ > 3))
  }

  test("exists false when predicate doesn't match") {
    assert(!Nullable(5).exists(_ > 10))
  }

  test("exists false on empty") {
    assert(!Nullable.empty[Int].exists(_ => true))
  }

  test("forall true when predicate matches") {
    assert(Nullable(5).forall(_ > 3))
  }

  test("forall true on empty") {
    assert(Nullable.empty[Int].forall(_ => false))
  }

  test("forall false when predicate doesn't match") {
    assert(!Nullable(5).forall(_ > 10))
  }

  test("contains true for matching value") {
    assert(Nullable("hello").contains("hello"))
  }

  test("contains false for non-matching value") {
    assert(!Nullable("hello").contains("world"))
  }

  test("contains false on empty") {
    assert(!Nullable.empty[String].contains("hello"))
  }

  test("filter keeps matching value") {
    val n = Nullable(5).filter(_ > 3)
    assert(n.isDefined)
    assertEquals(n.get, 5)
  }

  test("filter removes non-matching value") {
    val n = Nullable(5).filter(_ > 10)
    assert(n.isEmpty)
  }

  test("filter on empty returns empty") {
    assert(Nullable.empty[Int].filter(_ => true).isEmpty)
  }

  // --- Nullable wrapping Nullable (NestedNone) ---

  test("Nullable of Nullable.empty is non-empty") {
    val inner: Nullable[String] = Nullable.empty
    val outer: Nullable[Nullable[String]] = Nullable(inner)
    assert(outer.isDefined)
    assert(outer.flatten.isEmpty)
  }

  test("nested Nullable flatten preserves value") {
    val n: Nullable[Nullable[Int]] = Nullable(Nullable(42))
    assertEquals(n.flatten.get, 42)
  }

  test("double-nested empty Nullable flatten works") {
    val inner: Nullable[Int] = Nullable.empty
    val mid: Nullable[Nullable[Int]] = Nullable(inner)
    assert(mid.isDefined)
    assert(mid.flatten.isEmpty)

    val outer: Nullable[Nullable[Nullable[Int]]] = Nullable(mid)
    assert(outer.isDefined)
    assert(outer.flatten.isDefined)
    assert(outer.flatten.flatten.isEmpty)
  }

  // --- Allocation avoidance ---

  test("non-null value is stored directly without wrapper allocation") {
    val value = "hello"
    val n     = Nullable(value)
    assert(n.get eq value)
  }

  test("empty Nullable reuses cached NestedNone instance") {
    val a = Nullable.empty[String]
    val b = Nullable.empty[Int]
    assert(a.asInstanceOf[AnyRef] eq b.asInstanceOf[AnyRef])
  }

  test("wrapping Nullable.empty reuses cached NestedNone") {
    val empty1: Nullable[String] = Nullable.empty
    val wrapped1: Nullable[Nullable[String]] = Nullable(empty1)

    val empty2: Nullable[Int] = Nullable.empty
    val wrapped2: Nullable[Nullable[Int]] = Nullable(empty2)

    assert(wrapped1.asInstanceOf[AnyRef] eq wrapped2.asInstanceOf[AnyRef])
  }

  test("nested NestedNone up to depth 9 reuses cached instances") {
    for (depth <- 1 to 9) {
      var a: AnyRef = Nullable.empty[Any].asInstanceOf[AnyRef]
      for (_ <- 1 to depth)
        a = Nullable(a.asInstanceOf[Nullable[Any]]).asInstanceOf[AnyRef]

      var b: AnyRef = Nullable.empty[Any].asInstanceOf[AnyRef]
      for (_ <- 1 to depth)
        b = Nullable(b.asInstanceOf[Nullable[Any]]).asInstanceOf[AnyRef]

      assert(a eq b, s"NestedNone at depth $depth not cached (different instances)")
    }
  }

  // --- Primitive types ---

  test("Nullable with Int") {
    val n = Nullable(42)
    assert(n.isDefined)
    assertEquals(n.get, 42)
    assertEquals(n.map(_ * 2).get, 84)
  }

  test("Nullable with Boolean") {
    val n = Nullable(true)
    assert(n.isDefined)
    assertEquals(n.get, true)
  }

  test("Nullable with Double") {
    val n = Nullable(3.14)
    assert(n.isDefined)
    assertEqualsDouble(n.get, 3.14, 0.001)
  }
}
