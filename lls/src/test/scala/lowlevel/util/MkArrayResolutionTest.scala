package lowlevel
package util

class MkArrayResolutionTest extends munit.FunSuite {

  opaque type Pixels = Int
  object Pixels {
    def apply(v: Int): Pixels = v
    extension (p: Pixels) def toInt: Int = p
    given MkArray.OfInts[Pixels] = MkArray.ofIntAs[Pixels]
  }

  test("summonInline finds OfInts[Pixels] as MkArray[Pixels]") {
    // This is what collection factories do internally
    val mk = scala.compiletime.summonInline[MkArray[Pixels]]
    val arr = mk.create(3)
    mk.set(arr, 0, Pixels(42))
    assertEquals(mk.get(arr, 0).toInt, 42)
  }

  test("DynamicArray factory works with OfInts[Pixels] given") {
    val da = DynamicArray[Pixels]()
    da.add(Pixels(1))
    da.add(Pixels(2))
    assertEquals(da.size, 2)
    assertEquals(da(0).toInt, 1)
  }

  test("ObjectMap factory works with OfInts[Pixels] given") {
    val map = ObjectMap[Pixels, String]()
    map.put(Pixels(1), "one")
    assertEquals(map.get(Pixels(1)).getOrElse(fail("missing")), "one")
  }
}
