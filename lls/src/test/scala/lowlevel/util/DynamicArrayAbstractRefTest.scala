package lowlevel
package util

import scala.reflect.ClassTag

/** Regression test for ISS-686: `DynamicArray.foreach` (and the sibling inline methods `exists`/`find`/`count`/`forall`/`indexWhere`) threw
  * `ClassCastException: [Ljava.lang.Object; cannot be cast to [L<Bound>;` when the backing array was an `Object[]` (built via `MkArray.anyRef[AnyRef]`, e.g. through `DynamicArray.wrapRefUnchecked`)
  * AND the element type `T` was an abstract ref type whose upper bound was NOT `AnyRef` (e.g. `T <: Base`).
  *
  * The inline body's `mk0.castArray(_items)` (with witness `B = A`) inlined a whole-array reified CHECKCAST of the `Object[]` backing to `Bound[]` at the call site, which the JVM rejects. The fix
  * witnesses `B = AnyRef` in the `OfRefs`/fallback branches of `MkArray.withResolved`, so the array is typed `Array[AnyRef]` (no whole-array cast) and only per-element `.asInstanceOf[A]` narrows.
  */
class DynamicArrayAbstractRefTest extends munit.FunSuite {

  // A tiny local hierarchy whose abstract base is NOT `AnyRef`, reproducing the
  // abstract-bound element type that triggered the crash.
  sealed abstract class Base { def value: Int }
  final class Leaf(val value: Int) extends Base

  // Generic over `T <: Base`: at this call site the inlined `items` would erase
  // to `Base[]`, while the runtime backing array is `Object[]`.
  private def iterSum[T <: Base](da: DynamicArray[T]): Int = {
    var sum = 0
    da.foreach(t => sum += t.value)
    sum
  }

  private def buildObjectBacked[T <: Base: ClassTag](elems: T*): DynamicArray[T] = {
    // `wrapRefUnchecked` uses `MkArray.anyRef[AnyRef]` -> Object[] backing,
    // mirroring how sge builds these arrays for abstract ref element types.
    val da = DynamicArray.wrapRefUnchecked(new Array[T](0))
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
