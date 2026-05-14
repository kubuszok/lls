package lowlevel
package util

class MkArrayBackingTypeTest extends munit.FunSuite {

  opaque type Pixels = Int
  object Pixels {
    def apply(v: Int): Pixels                 = v
    given mk:          MkArray.OfInts[Pixels] = MkArray.mkInt.asInstanceOf[MkArray.OfInts[Pixels]]
  }

  opaque type Seconds = Float
  object Seconds {
    def apply(v: Float): Seconds                   = v
    given mk:            MkArray.OfFloats[Seconds] = MkArray.mkFloat.asInstanceOf[MkArray.OfFloats[Seconds]]
  }

  opaque type Millis = Long
  object Millis {
    def apply(v: Long): Millis                  = v
    given mk:           MkArray.OfLongs[Millis] = MkArray.mkLong.asInstanceOf[MkArray.OfLongs[Millis]]
  }

  opaque type Flags = Byte
  object Flags {
    def apply(v: Byte): Flags                  = v
    given mk:           MkArray.OfBytes[Flags] = MkArray.mkByte.asInstanceOf[MkArray.OfBytes[Flags]]
  }

  private def assertBackingType[A](da: DynamicArray[A], expectedComponentType: Class[?])(using loc: munit.Location): Unit = {
    val itemsField = da.getClass.getDeclaredField("_items")
    itemsField.setAccessible(true)
    val backingArray = itemsField.get(da)
    assertEquals(
      backingArray.getClass.getComponentType,
      expectedComponentType,
      s"Expected backing array component type ${expectedComponentType.getName}"
    )
  }

  test("Int-backed opaque type uses Array[Int] backing") {
    assertBackingType(DynamicArray[Pixels](4), classOf[Int])
  }

  test("Float-backed opaque type uses Array[Float] backing") {
    assertBackingType(DynamicArray[Seconds](4), classOf[Float])
  }

  test("Long-backed opaque type uses Array[Long] backing") {
    assertBackingType(DynamicArray[Millis](4), classOf[Long])
  }

  test("Byte-backed opaque type uses Array[Byte] backing") {
    assertBackingType(DynamicArray[Flags](4), classOf[Byte])
  }

  test("All primitive MkArray instances produce correct array types") {
    assertEquals(MkArray.mkInt.create(1).getClass.getComponentType, classOf[Int])
    assertEquals(MkArray.mkLong.create(1).getClass.getComponentType, classOf[Long])
    assertEquals(MkArray.mkFloat.create(1).getClass.getComponentType, classOf[Float])
    assertEquals(MkArray.mkDouble.create(1).getClass.getComponentType, classOf[Double])
    assertEquals(MkArray.mkByte.create(1).getClass.getComponentType, classOf[Byte])
    assertEquals(MkArray.mkShort.create(1).getClass.getComponentType, classOf[Short])
    assertEquals(MkArray.mkChar.create(1).getClass.getComponentType, classOf[Char])
    assertEquals(MkArray.mkBoolean.create(1).getClass.getComponentType, classOf[Boolean])
    assertEquals(MkArray.anyRef[String].create(1).getClass.getComponentType, classOf[String])
  }
}
