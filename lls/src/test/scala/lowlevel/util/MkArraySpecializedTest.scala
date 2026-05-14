package lowlevel
package util

class MkArraySpecializedTest extends munit.FunSuite {

  opaque type Pixels = Int
  object Pixels {
    def apply(v:  Int):              Pixels                 = v
    extension (p: Pixels) def toInt: Int                    = p
    given mk:                        MkArray.OfInts[Pixels] = MkArray.mkInt.asInstanceOf[MkArray.OfInts[Pixels]]
  }

  opaque type Seconds = Float
  object Seconds {
    def apply(v:  Float):               Seconds                   = v
    extension (s: Seconds) def toFloat: Float                     = s
    given mk:                           MkArray.OfFloats[Seconds] = MkArray.mkFloat.asInstanceOf[MkArray.OfFloats[Seconds]]
  }

  opaque type Millis = Long
  object Millis {
    def apply(v:  Long):              Millis                  = v
    extension (m: Millis) def toLong: Long                    = m
    given mk:                         MkArray.OfLongs[Millis] = MkArray.mkLong.asInstanceOf[MkArray.OfLongs[Millis]]
  }

  opaque type Flags = Byte
  object Flags {
    def apply(v:  Byte):             Flags                  = v
    extension (f: Flags) def toByte: Byte                   = f
    given mk:                        MkArray.OfBytes[Flags] = MkArray.mkByte.asInstanceOf[MkArray.OfBytes[Flags]]
  }

  test("DynamicArray[Pixels] stores and retrieves values correctly") {
    val da = DynamicArray[Pixels](4)
    da.add(Pixels(100))
    da.add(Pixels(200))
    da.add(Pixels(300))
    assertEquals(da.size, 3)
    assertEquals(da(0).toInt, 100)
    assertEquals(da(1).toInt, 200)
    assertEquals(da(2).toInt, 300)
  }

  test("DynamicArray[Seconds] stores and retrieves values correctly") {
    val da = DynamicArray[Seconds](4)
    da.add(Seconds(1.5f))
    da.add(Seconds(2.0f))
    assertEquals(da.size, 2)
    assertEquals(da(0).toFloat, 1.5f)
    assertEquals(da(1).toFloat, 2.0f)
  }

  test("DynamicArray[Millis] stores and retrieves values correctly") {
    val da = DynamicArray[Millis](4)
    da.add(Millis(1000L))
    da.add(Millis(2000L))
    assertEquals(da.size, 2)
    assertEquals(da(0).toLong, 1000L)
    assertEquals(da(1).toLong, 2000L)
  }

  test("DynamicArray[Pixels] foreach works") {
    val da = DynamicArray[Pixels](4)
    da.add(Pixels(10))
    da.add(Pixels(20))
    da.add(Pixels(30))
    var sum = 0
    da.foreach(p => sum += p.toInt)
    assertEquals(sum, 60)
  }

  test("DynamicArray[Pixels] contains and indexOf work") {
    val da = DynamicArray[Pixels](4)
    da.add(Pixels(10))
    da.add(Pixels(20))
    da.add(Pixels(30))
    assert(da.contains(Pixels(20)))
    assert(!da.contains(Pixels(99)))
    assertEquals(da.indexOf(Pixels(30)), 2)
  }

  test("DynamicArray[Pixels] sort works") {
    val da = DynamicArray[Pixels](4)
    da.add(Pixels(30))
    da.add(Pixels(10))
    da.add(Pixels(20))
    given Ordering[Pixels] = Ordering.Int.asInstanceOf[Ordering[Pixels]]
    da.sort()
    assertEquals(da(0).toInt, 10)
    assertEquals(da(1).toInt, 20)
    assertEquals(da(2).toInt, 30)
  }

  test("ObjectMap with opaque key type") {
    val map = ObjectMap[Pixels, String]()
    map.put(Pixels(1), "one")
    map.put(Pixels(2), "two")
    assertEquals(map.size, 2)
    assertEquals(map.get(Pixels(1)).getOrElse(fail("missing")), "one")
    assertEquals(map.get(Pixels(2)).getOrElse(fail("missing")), "two")
  }

  test("ObjectSet with opaque type") {
    val set = ObjectSet[Pixels]()
    assert(set.add(Pixels(10)))
    assert(set.add(Pixels(20)))
    assert(!set.add(Pixels(10)))
    assertEquals(set.size, 2)
    assert(set.contains(Pixels(10)))
  }

}
