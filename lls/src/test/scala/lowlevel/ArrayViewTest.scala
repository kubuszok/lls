package lowlevel

class ArrayViewTest extends munit.FunSuite {

  // --- foreach ---

  test("leanView foreach iterates all elements") {
    val arr = Array(1, 2, 3, 4, 5)
    var sum = 0
    arr.leanView.foreach(sum += _)
    assertEquals(sum, 15)
  }

  test("leanView foreach with strings") {
    val arr    = Array("a", "bb", "ccc")
    var result = 0
    arr.leanView.foreach(s => result += s.length)
    assertEquals(result, 6)
  }

  test("empty array foreach is no-op") {
    val arr    = Array.empty[Int]
    var called = false
    arr.leanView.foreach(_ => called = true)
    assert(!called)
  }

  test("length") {
    assertEquals(Array(1, 2, 3).leanView.length, 3)
    assertEquals(Array.empty[String].leanView.length, 0)
  }

  // --- map (yield) ---

  test("map produces new array") {
    val arr    = Array(1, 2, 3)
    val result = arr.leanView.map(_ * 10)
    assertEquals(result.toSeq, Seq(10, 20, 30))
  }

  test("map with type change") {
    val arr    = Array(1, 2, 3)
    val result = arr.leanView.map(_.toString)
    assertEquals(result.toSeq, Seq("1", "2", "3"))
  }

  // --- flatMap ---

  test("flatMap expands elements") {
    val arr    = Array(1, 2, 3)
    val result = arr.leanView.flatMap(x => Array(x, x * 10))
    assertEquals(result.toSeq, Seq(1, 10, 2, 20, 3, 30))
  }

  // --- withFilter (guards) ---

  test("withFilter foreach skips non-matching") {
    val arr = Array(1, 2, 3, 4, 5)
    var sum = 0
    arr.leanView.withFilter(_ % 2 == 0).foreach(sum += _)
    assertEquals(sum, 6)
  }

  test("withFilter map produces filtered array") {
    val arr    = Array(1, 2, 3, 4, 5)
    val result = arr.leanView.withFilter(_ > 3).map(_ * 2)
    assertEquals(result.toSeq, Seq(8, 10))
  }

  test("withFilter flatMap") {
    val arr    = Array(1, 2, 3, 4)
    val result = arr.leanView.withFilter(_ % 2 == 0).flatMap(x => Array(x, -x))
    assertEquals(result.toSeq, Seq(2, -2, 4, -4))
  }

  test("chained withFilter") {
    val arr = Array(1, 2, 3, 4, 5, 6, 7, 8)
    var sum = 0
    arr.leanView.withFilter(_ > 2).withFilter(_ < 7).foreach(sum += _)
    assertEquals(sum, 3 + 4 + 5 + 6)
  }

  // --- zipWithIndex ---

  test("zipWithIndex foreach provides element and index") {
    val arr     = Array("x", "y", "z")
    val builder = new StringBuilder
    arr.leanView.zipWithIndex.foreach { (s, i) =>
      builder.append(s"$i:$s ")
    }
    assertEquals(builder.toString.trim, "0:x 1:y 2:z")
  }

  test("zipWithIndex map") {
    val arr    = Array("a", "b", "c")
    val result = arr.leanView.zipWithIndex.map((s, i) => s"$i$s")
    assertEquals(result.toSeq, Seq("0a", "1b", "2c"))
  }

  test("zipWithIndex withFilter foreach") {
    val arr     = Array("a", "b", "c", "d")
    val builder = new StringBuilder
    arr.leanView.zipWithIndex.withFilter((_, i) => i % 2 == 0).foreach { (s, _) =>
      builder.append(s)
    }
    assertEquals(builder.toString, "ac")
  }

  // --- for comprehensions ---

  test("for comprehension single generator") {
    val arr = Array(10, 20, 30)
    var sum = 0
    for (a <- arr.leanView)
      sum += a
    assertEquals(sum, 60)
  }

  test("for comprehension with yield") {
    val arr    = Array(1, 2, 3)
    val result = for (a <- arr.leanView) yield a * 2
    assertEquals(result.toSeq, Seq(2, 4, 6))
  }

  test("for comprehension with guard") {
    val arr = Array(1, 2, 3, 4, 5)
    var sum = 0
    for {
      a <- arr.leanView
      if a % 2 == 1
    } sum += a
    assertEquals(sum, 9)
  }

  test("for comprehension with guard and yield") {
    val arr    = Array(1, 2, 3, 4, 5)
    val result = for {
      a <- arr.leanView
      if a > 2
    } yield a * 10
    assertEquals(result.toSeq, Seq(30, 40, 50))
  }

  test("for comprehension with zipWithIndex") {
    val arr     = Array("a", "b", "c")
    val builder = new StringBuilder
    for ((s, i) <- arr.leanView.zipWithIndex)
      builder.append(s"$i$s")
    assertEquals(builder.toString, "0a1b2c")
  }

  test("nested for comprehension") {
    val arr1 = Array(1, 2)
    val arr2 = Array(10, 20)
    var sum  = 0
    for {
      a <- arr1.leanView
      b <- arr2.leanView
    } sum += a + b
    assertEquals(sum, (1 + 10) + (1 + 20) + (2 + 10) + (2 + 20))
  }

  test("nested for comprehension with yield") {
    val arr1   = Array(1, 2)
    val arr2   = Array(10, 20)
    val result = for {
      a <- arr1.leanView
    } yield
      for {
        b <- arr2.leanView
      } yield a + b
    assertEquals(result.map(_.toSeq).toSeq, Seq(Seq(11, 21), Seq(12, 22)))
  }

  test("nested for comprehension with zipWithIndex") {
    val arr1    = Array("a", "b")
    val arr2    = Array("x", "y")
    val builder = new StringBuilder
    for {
      a <- arr1.leanView
      (b, i) <- arr2.leanView.zipWithIndex
    } builder.append(s"$a$b$i,")
    assertEquals(builder.toString, "ax0,ay1,bx0,by1,")
  }

  // --- IArray views ---

  test("IArray leanView foreach") {
    val arr = IArray(1, 2, 3)
    var sum = 0
    arr.leanView.foreach(sum += _)
    assertEquals(sum, 6)
  }

  test("IArray leanView map") {
    val arr    = IArray(1, 2, 3)
    val result = arr.leanView.map(_ * 2)
    assertEquals(result.toSeq, Seq(2, 4, 6))
  }

  test("IArray leanView flatMap") {
    val arr    = IArray(1, 2)
    val result = arr.leanView.flatMap(x => Array(x, x * 10))
    assertEquals(result.toSeq, Seq(1, 10, 2, 20))
  }

  test("IArray leanView withFilter foreach") {
    val arr = IArray(1, 2, 3, 4, 5)
    var sum = 0
    arr.leanView.withFilter(_ > 3).foreach(sum += _)
    assertEquals(sum, 9)
  }

  test("IArray leanView zipWithIndex foreach") {
    val arr     = IArray("a", "b")
    val builder = new StringBuilder
    arr.leanView.zipWithIndex.foreach((s, i) => builder.append(s"$i$s"))
    assertEquals(builder.toString, "0a1b")
  }

  test("IArray for comprehension with guard and yield") {
    val arr    = IArray(1, 2, 3, 4, 5)
    val result = for {
      a <- arr.leanView
      if a % 2 == 0
    } yield a * 10
    assertEquals(result.toSeq, Seq(20, 40))
  }

  test("IArray length") {
    assertEquals(IArray(1, 2, 3).leanView.length, 3)
  }

  // --- Multiple arrays interleaved ---

  test("three nested arrays foreach") {
    val a1      = Array(1, 2)
    val a2      = Array(10, 20)
    val a3      = Array(100, 200)
    val builder = new StringBuilder
    for {
      x <- a1.leanView
      y <- a2.leanView
      z <- a3.leanView
    } builder.append(s"${x + y + z},")
    assertEquals(builder.toString, "111,211,121,221,112,212,122,222,")
  }

  test("mixed Array and IArray in nested for") {
    val arr     = Array("a", "b")
    val iarr    = IArray(1, 2)
    val builder = new StringBuilder
    for {
      s <- arr.leanView
      n <- iarr.leanView
    } builder.append(s"$s$n,")
    assertEquals(builder.toString, "a1,a2,b1,b2,")
  }

  test("Array + IArray + zipWithIndex nested") {
    val arr     = Array("x", "y")
    val iarr    = IArray(10, 20)
    val builder = new StringBuilder
    for {
      s <- arr.leanView
      (n, i) <- iarr.leanView.zipWithIndex
    } builder.append(s"$s$n@$i,")
    assertEquals(builder.toString, "x10@0,x20@1,y10@0,y20@1,")
  }

  // --- Guard + zipWithIndex interleaved ---

  test("outer guard, inner zipWithIndex") {
    val a1      = Array(1, 2, 3, 4)
    val a2      = Array("a", "b")
    val builder = new StringBuilder
    for {
      x <- a1.leanView
      if x % 2 == 0
      (s, i) <- a2.leanView.zipWithIndex
    } builder.append(s"$x$s$i,")
    assertEquals(builder.toString, "2a0,2b1,4a0,4b1,")
  }

  test("outer zipWithIndex, inner guard") {
    val a1      = Array("a", "b", "c")
    val a2      = Array(1, 2, 3, 4)
    val builder = new StringBuilder
    for {
      (s, si) <- a1.leanView.zipWithIndex
      n <- a2.leanView
      if n > 2
    } builder.append(s"$s$si:$n,")
    assertEquals(builder.toString, "a0:3,a0:4,b1:3,b1:4,c2:3,c2:4,")
  }

  test("guard on zipWithIndex then nested array") {
    val a1      = Array(10, 20, 30, 40)
    val a2      = Array("x", "y")
    val builder = new StringBuilder
    for {
      (n, i) <- a1.leanView.zipWithIndex
      if i % 2 == 1
      s <- a2.leanView
    } builder.append(s"$n$s,")
    assertEquals(builder.toString, "20x,20y,40x,40y,")
  }

  // --- Multiple guards ---

  test("multiple guards on same array") {
    val arr = Array(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    var sum = 0
    for {
      x <- arr.leanView
      if x > 3
      if x < 8
      if x % 2 == 1
    } sum += x
    assertEquals(sum, 5 + 7)
  }

  test("guard on outer + guard on inner") {
    val a1  = Array(1, 2, 3)
    val a2  = Array(10, 20, 30)
    var sum = 0
    for {
      x <- a1.leanView
      if x != 2
      y <- a2.leanView
      if y != 20
    } sum += x * y
    assertEquals(sum, 1 * 10 + 1 * 30 + 3 * 10 + 3 * 30)
  }

  // --- yield with multiple generators ---

  test("nested for yield produces nested arrays") {
    val a1     = Array(1, 2)
    val a2     = Array(10, 20)
    val result = for (a <- a1.leanView) yield for (b <- a2.leanView) yield a + b
    assertEquals(result.map(_.toSeq).toSeq, Seq(Seq(11, 21), Seq(12, 22)))
  }

  test("yield with guard") {
    val arr    = Array(1, 2, 3, 4, 5, 6)
    val result = for {
      x <- arr.leanView
      if x % 3 == 0
    } yield x * 100
    assertEquals(result.toSeq, Seq(300, 600))
  }

  test("yield with zipWithIndex") {
    val arr    = Array("a", "b", "c")
    val result = for ((s, i) <- arr.leanView.zipWithIndex) yield s"$i=$s"
    assertEquals(result.toSeq, Seq("0=a", "1=b", "2=c"))
  }

  test("IArray yield with guard") {
    val arr    = IArray(10, 20, 30, 40, 50)
    val result = for {
      x <- arr.leanView
      if x >= 30
    } yield x / 10
    assertEquals(result.toSeq, Seq(3, 4, 5))
  }

  test("IArray yield with zipWithIndex") {
    val arr    = IArray("x", "y", "z")
    val result = for ((s, i) <- arr.leanView.zipWithIndex) yield s * (i + 1)
    assertEquals(result.toSeq, Seq("x", "yy", "zzz"))
  }

  // --- flatMap edge cases ---

  test("flatMap producing empty arrays") {
    val arr    = Array(1, 2, 3)
    val result = arr.leanView.flatMap(x => if (x == 2) Array(x * 10) else Array.empty[Int])
    assertEquals(result.toSeq, Seq(20))
  }

  test("flatMap with guard") {
    val arr    = Array(1, 2, 3, 4)
    val result = arr.leanView.withFilter(_ % 2 == 0).flatMap(x => Array(x, -x))
    assertEquals(result.toSeq, Seq(2, -2, 4, -4))
  }

  // --- Single element and empty edge cases ---

  test("single element array operations") {
    val arr = Array(42)
    assertEquals(arr.leanView.length, 1)
    assertEquals(arr.leanView.map(_ + 1).toSeq, Seq(43))

    var sum = 0
    arr.leanView.foreach(sum += _)
    assertEquals(sum, 42)

    val builder = new StringBuilder
    for ((v, i) <- arr.leanView.zipWithIndex) builder.append(s"$i:$v")
    assertEquals(builder.toString, "0:42")
  }

  test("empty array all operations") {
    val arr = Array.empty[Int]
    assertEquals(arr.leanView.length, 0)
    assertEquals(arr.leanView.map(_ + 1).toSeq, Seq.empty)
    assertEquals(arr.leanView.flatMap(x => Array(x)).toSeq, Seq.empty)

    var called = false
    arr.leanView.withFilter(_ => true).foreach(_ => called = true)
    assert(!called)

    arr.leanView.zipWithIndex.foreach((_, _) => called = true)
    assert(!called)
  }

  test("empty IArray all operations") {
    val arr = IArray.empty[String]
    assertEquals(arr.leanView.length, 0)
    assertEquals(arr.leanView.map(_.length).toSeq, Seq.empty)

    var called = false
    arr.leanView.foreach(_ => called = true)
    assert(!called)
  }

  // --- zipWithIndex + guard + map combination ---

  test("zipWithIndex filtered then mapped") {
    val arr    = Array("a", "b", "c", "d", "e")
    val result = arr.leanView.zipWithIndex.withFilter((_, i) => i >= 2).map((s, i) => s"$s@$i")
    assertEquals(result.toSeq, Seq("c@2", "d@3", "e@4"))
  }

  test("for comprehension zipWithIndex + guard + yield") {
    val arr    = Array(10, 20, 30, 40, 50)
    val result = for {
      (v, i) <- arr.leanView.zipWithIndex
      if i % 2 == 0
    } yield v
    assertEquals(result.toSeq, Seq(10, 30, 50))
  }
}
