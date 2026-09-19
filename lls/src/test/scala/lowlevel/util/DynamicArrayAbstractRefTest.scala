package lowlevel
package util

import scala.reflect.ClassTag

/** Regression test for ISS-686: DynamicArray with Object[]-backed arrays and abstract ref element types. An inline member expanded where the element type is known must not cast the whole `Object[]`
  * store to the element's array type.
  */
class DynamicArrayAbstractRefTest extends munit.FunSuite {

  sealed abstract class Base { def value: Int }
  final class Leaf(val value: Int) extends Base

  private def iterSum[T <: Base](da: DynamicArray[T]): Int = {
    var sum = 0
    da.foreach(t => sum += t.value)
    sum
  }

  private def buildObjectBacked[T <: Base: ClassTag](elems: T*): DynamicArray[T] = {
    // the reference type class -> an `Object[]` backing, which is how sge builds these arrays for
    // abstract ref element types (its `createRef`)
    val da = new DynamicArray[T]()(using MkArray.anyRef[AnyRef].asInstanceOf[MkArray[T]])
    elems.foreach(da.add)
    da
  }

  test("foreach over Object[]-backed DynamicArray with abstract-bound element type") {
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    assertEquals(iterSum(da), 6)
  }

  test("exists over Object[]-backed DynamicArray with abstract-bound element type") {
    def go[T <: Base](da: DynamicArray[T]): Boolean = da.exists(_.value == 2)
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    assert(go(da))
  }

  test("find over Object[]-backed DynamicArray with abstract-bound element type") {
    def go[T <: Base](da: DynamicArray[T]): Nullable[T] = da.find(_.value == 2)
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    val found = go(da)
    assert(found.isDefined)
    assertEquals(found.get.value, 2)
  }

  test("count over Object[]-backed DynamicArray with abstract-bound element type") {
    def go[T <: Base](da: DynamicArray[T]): Int = da.count(_.value > 1)
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    assertEquals(go(da), 2)
  }

  test("forall over Object[]-backed DynamicArray with abstract-bound element type") {
    def go[T <: Base](da: DynamicArray[T]): Boolean = da.forall(_.value > 0)
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    assert(go(da))
  }

  test("indexWhere over Object[]-backed DynamicArray with abstract-bound element type") {
    def go[T <: Base](da: DynamicArray[T]): Int = da.indexWhere(_.value == 3)
    val da = buildObjectBacked[Leaf](new Leaf(1), new Leaf(2), new Leaf(3))
    assertEquals(go(da), 2)
  }
}
