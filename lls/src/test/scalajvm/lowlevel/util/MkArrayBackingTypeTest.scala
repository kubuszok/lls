package lowlevel
package util

class MkArrayBackingTypeTest extends munit.FunSuite {

  opaque type Pixels = Int
  object Pixels {
    def apply(v: Int): Pixels                 = v
    given mk:          MkArray.OfInts[Pixels] = MkArray.ofIntAs[Pixels]
  }

  opaque type Seconds = Float
  object Seconds {
    def apply(v: Float): Seconds                   = v
    given mk:            MkArray.OfFloats[Seconds] = MkArray.ofFloatAs[Seconds]
  }

  opaque type Millis = Long
  object Millis {
    def apply(v: Long): Millis                  = v
    given mk:           MkArray.OfLongs[Millis] = MkArray.ofLongAs[Millis]
  }

  opaque type Flags = Byte
  object Flags {
    def apply(v: Byte): Flags                  = v
    given mk:           MkArray.OfBytes[Flags] = MkArray.ofByteAs[Flags]
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
    assertEquals(MkArray.ofInt.create(1).getClass.getComponentType, classOf[Int])
    assertEquals(MkArray.ofLong.create(1).getClass.getComponentType, classOf[Long])
    assertEquals(MkArray.ofFloat.create(1).getClass.getComponentType, classOf[Float])
    assertEquals(MkArray.ofDouble.create(1).getClass.getComponentType, classOf[Double])
    assertEquals(MkArray.ofByte.create(1).getClass.getComponentType, classOf[Byte])
    assertEquals(MkArray.ofShort.create(1).getClass.getComponentType, classOf[Short])
    assertEquals(MkArray.ofChar.create(1).getClass.getComponentType, classOf[Char])
    assertEquals(MkArray.ofBoolean.create(1).getClass.getComponentType, classOf[Boolean])
    assertEquals(MkArray.anyRef[String].create(1).getClass.getComponentType, classOf[String])
  }
}
