package lowlevel
package util

/** The sorting and selection algorithms were written for object arrays; these arrays are primitive-backed. */
class PrimitiveBackedAlgorithmsTest extends munit.FunSuite {

  private def ints(values: Int*): DynamicArray[Int] = {
    val a = DynamicArray[Int](values.length.max(1))
    values.foreach(a.add)
    a
  }

  private def contents[A](a: DynamicArray[A]): List[A] = {
    val b = List.newBuilder[A]
    a.foreach(b += _)
    b.result()
  }

  test("sort() orders an Int array") {
    val a = ints(3, 1, 2, -7, 40, 0)
    a.sort()
    assertEquals(contents(a), List(-7, 0, 1, 2, 3, 40))
  }

  test("sort(ordering) orders an Int array") {
    val a = ints(3, 1, 2, -7, 40, 0)
    a.sort(Ordering.Int.reverse)
    assertEquals(contents(a), List(40, 3, 2, 1, 0, -7))
  }

  test("sort(ordering) orders a Float array and a Long array") {
    val f = DynamicArray[Float](4)
    List(2.5f, -1f, 0f).foreach(f.add)
    f.sort(Ordering.Float.TotalOrdering)
    assertEquals(contents(f), List(-1f, 0f, 2.5f))
    val l = DynamicArray[Long](4)
    List(5L, Long.MinValue, 1L).foreach(l.add)
    l.sort(Ordering.Long)
    assertEquals(contents(l), List(Long.MinValue, 1L, 5L))
  }

  test("sort leaves the unused capacity alone and handles more elements than the insertion-sort threshold") {
    val n = 500
    val a = DynamicArray[Int](2 * n)
    var i = 0
    while (i < n) { a.add((i * 7919) % n); i += 1 }
    a.sort()
    assertEquals(a.size, n)
    assertEquals(contents(a), (0 until n).toList)
  }

  test("sort still orders a reference array in place") {
    val a = DynamicArray[String](4)
    List("b", "c", "a").foreach(a.add)
    a.sort(Ordering.String)
    assertEquals(contents(a), List("a", "b", "c"))
    a.sort(Ordering.String.reverse)
    assertEquals(contents(a), List("c", "b", "a"))
  }

  test("selectRanked finds the k-th lowest of an Int array") {
    val values = List(9, 4, 7, 1, 8, 2, 6)
    val sorted = values.sorted
    (1 to values.length).foreach { k =>
      assertEquals(ints(values*).selectRanked(Ordering.Int, k), sorted(k - 1), s"k=$k")
    }
  }

  test("selectRankedIndex answers an index whose element is the k-th lowest") {
    val values = List(9, 4, 7, 1, 8, 2, 6)
    val sorted = values.sorted
    (1 to values.length).foreach { k =>
      val a = ints(values*)
      val i = a.selectRankedIndex(Ordering.Int, k)
      assertEquals(a.get(i), sorted(k - 1), s"k=$k")
    }
  }

  test("an ArrayMap with primitive keys and values copies them into a given array") {
    val m = ArrayMap[Int, Long]()
    m.put(1, 10L)
    m.put(2, 20L)
    val ks = DynamicArray[AnyRef](4)
    val vs = DynamicArray[AnyRef](4)
    m.keys().toArray(ks)
    m.values().toArray(vs)
    assertEquals(contents(ks), List[AnyRef](Int.box(1), Int.box(2)))
    assertEquals(contents(vs), List[AnyRef](Long.box(10L), Long.box(20L)))
  }
}
