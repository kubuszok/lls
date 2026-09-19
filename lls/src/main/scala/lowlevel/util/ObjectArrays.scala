package lowlevel
package util

/** Runs code written for an object array over an array that may be primitive-backed.
  *
  * The ported sorting and selection algorithms work on `Array[AnyRef]`, as their Java originals work on `Object[]`. A `DynamicArray[Int]` is backed by an `int[]`, which is not one. For a reference
  * array the code runs in place; for a primitive array the range is boxed into a temporary, the code runs on that, and the (reordered) range is written back.
  */
private[lowlevel] object ObjectArrays {

  // The sorters keep scratch storage between calls so that sorting does not allocate; Java shares one
  // of each through `Sort.instance()`, which two threads sorting at once corrupt. One per thread
  // keeps the reuse without the sharing.
  private val timSorts = new ThreadLocal[TimSort[AnyRef]] {
    override def initialValue(): TimSort[AnyRef] = new TimSort[AnyRef]()
  }
  private val comparableTimSorts = new ThreadLocal[ComparableTimSort] {
    override def initialValue(): ComparableTimSort = new ComparableTimSort()
  }

  /** This thread's sorter for an explicit ordering. */
  def timSort: TimSort[AnyRef] = timSorts.get()

  /** This thread's sorter for `Comparable` elements. */
  def comparableTimSort: ComparableTimSort = comparableTimSorts.get()

  /** Applies `f` to `a`'s range `[from, to)` seen as an object array, and returns its result. `f` receives the array and the range to work on; an index it returns is relative to `from`. */
  def within[T, R](a: Array[T], from: Int, to: Int)(f: (Array[AnyRef], Int, Int) => R): R =
    (a: Any) match {
      case refs: Array[AnyRef] => f(refs, from, to)
      case _ =>
        // the checks the algorithms make on the array they are handed, made here on the real one
        if (from > to) throw new IllegalArgumentException("fromIndex(" + from + ") > toIndex(" + to + ")")
        if (from < 0) throw new ArrayIndexOutOfBoundsException(from)
        if (to > a.length) throw new ArrayIndexOutOfBoundsException(to)
        val n     = to - from
        val boxed = new Array[AnyRef](n)
        var i     = 0
        while (i < n) { boxed(i) = a(from + i).asInstanceOf[AnyRef]; i += 1 }
        val result = f(boxed, 0, n)
        i = 0
        while (i < n) { a(from + i) = boxed(i).asInstanceOf[T]; i += 1 }
        result
    }
}
