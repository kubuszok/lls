package lowlevel.port

/** Replacement bodies, keyed by the java member, for the sorting and selection code that treats every `T[]` as an `Object[]`: a `DynamicArray[Int]` is backed by an `int[]`, and that cast compiles and
  * throws. Each body keeps java's algorithm and hands it the array through the hand-written `lowlevel.util.ObjectArrays.within` — a reference array in place, a primitive range boxed into a temporary
  * and written back.
  */
object LlsPrimitiveArrays:

  private val U      = "com.badlogic.gdx.utils"
  private val Within = "lowlevel.util.ObjectArrays.within"
  private val Obj    = "java.lang.Object"
  private val ord    = s"c.asInstanceOf[scala.math.Ordering[$Obj]]"
  // Java shares ONE sorter of each kind through `Sort.instance()` to reuse its scratch storage, and
  // documents the entry points as not thread safe; the hand-written lls sorted safely from two
  // threads. The bodies take this THREAD's sorter instead (same reuse, measured: two test suites
  // sorting in parallel corrupted the shared one).
  private val tim    = "lowlevel.util.ObjectArrays.timSort"
  private val cmpTim = "lowlevel.util.ObjectArrays.comparableTimSort"

  /** the element copied out one at a time: a generic array read boxes a primitive, which is what an object array needs. */
  private def copyInto(field: String) =
    s"""{
       |  val src = this.map.$field
       |  var i: scala.Int = this.index
       |  while (i < this.map.size) { array.add(src(i).asInstanceOf[$Obj]); i += 1 }
       |  return array.asInstanceOf[lowlevel.util.DynamicArray[${if field.startsWith("keys") then "K" else "V"}]]
       |}""".stripMargin

  /** java compares two arrays element by element through its RAW view of the other one (`Object[]`). No cast is written there — the view comes from the raw `Array` type — so nothing counts it, and on
    * a primitive-backed array it throws. The elements are read through `get` instead, which boxes a primitive. `same` decides one pair; for identity on a primitive store, value equality is the only
    * meaning "the same element" has.
    */
  private def elementwise(same: String) =
    s"""{
       |  if (`object`.asInstanceOf[scala.AnyRef] eq this) { return true } else ()
       |  if (!this.ordered) { return false } else ()
       |  if (!`object`.isInstanceOf[lowlevel.util.DynamicArray[?]]) { return false } else ()
       |  val array: lowlevel.util.DynamicArray[?] = `object`.asInstanceOf[lowlevel.util.DynamicArray[?]]
       |  if (!array.ordered) { return false } else ()
       |  val n: scala.Int = this.size
       |  if (n != array.size) { return false } else ()
       |  var i: scala.Int = 0
       |  while (i < n) {
       |    val o1: $Obj = this.get(i).asInstanceOf[$Obj]
       |    val o2: $Obj = array.get(i).asInstanceOf[$Obj]
       |    if (!($same)) { return false } else ()
       |    i += 1
       |  }
       |  return true
       |}""".stripMargin

  val bodies: Map[String, String] = Map(
    s"$U.Array#equals(Object)" -> elementwise("if (o1 == null) o2 == null else o1.equals(o2)"),
    s"$U.Array#equalsIdentity(Object)" ->
      elementwise(
        s"if (this.items.isInstanceOf[scala.Array[scala.AnyRef]] && array.items.isInstanceOf[scala.Array[scala.AnyRef]]) o1 eq o2 else (if (o1 == null) o2 == null else o1.equals(o2))"
      ),
    s"$U.Array#sort()" ->
      s"{ $Within(this.items, 0, this.size)((refs, lo, hi) => lowlevel.util.Sort.instance().sort(refs, lo, hi)) }",
    s"$U.Sort#sort(Array)" ->
      s"{ $Within(a.items, 0, a.size)((refs, lo, hi) => $cmpTim.doSort(refs, lo, hi)) }",
    s"$U.Sort#sort(Object[])" ->
      s"{ $cmpTim.doSort(a, 0, a.length) }",
    s"$U.Sort#sort(Object[],int,int)" ->
      s"{ $cmpTim.doSort(a, fromIndex, toIndex) }",
    s"$U.Sort#sort(Array,Comparator)" ->
      s"{ $Within(a.items, 0, a.size)((refs, lo, hi) => $tim.doSort(refs, $ord, lo, hi)) }",
    s"$U.Sort#sort(T[],Comparator)" ->
      s"{ $Within(a, 0, a.length)((refs, lo, hi) => $tim.doSort(refs, $ord, lo, hi)) }",
    s"$U.Sort#sort(T[],Comparator,int,int)" ->
      s"{ $Within(a, fromIndex, toIndex)((refs, lo, hi) => $tim.doSort(refs, $ord, lo, hi)) }",
    // java's own body, with the quickselect arm handed the array through the view; the two
    // single-pass arms read the array generically and need nothing
    s"$U.Select#selectIndex(T[],Comparator,int,int)" ->
      s"""{
         |  if (size < 1) {
         |    throw new java.lang.RuntimeException("cannot select from empty array (size < 1)")
         |  } else {
         |    if (kthLowest > size) {
         |      throw new java.lang.RuntimeException((("Kth rank is larger than size. k: " + kthLowest) + ", size: ") + size)
         |    } else ()
         |  }
         |  var idx: scala.Int = 0
         |  if (kthLowest == 1) {
         |    idx = this.fastMin(items, comp, size)
         |  } else {
         |    if (kthLowest == size) {
         |      idx = this.fastMax(items, comp, size)
         |    } else {
         |      // a selector per call: it holds the array and the ordering as fields, and nothing worth reusing
         |      idx = $Within(items, 0, size)((refs, lo, hi) => new lowlevel.util.QuickSelect[$Obj]().select(refs, comp.asInstanceOf[scala.math.Ordering[$Obj]], kthLowest, hi))
         |    }
         |  }
         |  return idx
         |}""".stripMargin,
    s"$U.ArrayMap$$Keys#toArray(Array)" -> copyInto("keys$field"),
    s"$U.ArrayMap$$Values#toArray(Array)" -> copyInto("values$field")
  )
