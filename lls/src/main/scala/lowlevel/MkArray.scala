package lowlevel

import scala.reflect.ClassTag

/** Type class that abstracts primitive array operations, avoiding boxing overhead.
  *
  * ==Method Categories==
  *
  * '''Array lifecycle (no boxing risk — safe to call through erased `MkArray[A]`):'''
  *   - `create`, `copyOf`, `copyOfRange` — allocate/copy arrays
  *   - `nullOut`, `nullOutRange` — clear slots for GC (no-op for primitives)
  *
  * '''Element access (boxes through erased `MkArray[A]` trait interface — use `MkArray.withResolved` in `inline def` for zero-boxing):'''
  *   - `get`, `set` — read/write single elements
  *   - `elemEquals` — compare two values of type `A`
  *   - `castIn` — narrow `Any` to `A` (for bridging erased boundaries in inline code)
  *   - `castArray` — narrow `Array[?]` to `Array[A]` (for bridging erased boundaries in inline code)
  *
  * '''Array manipulation (no boxing — operates on arrays by index, never extracts `A` through parameters):'''
  *   - `swap` — exchange two elements in-place
  *   - `copyElement` — copy one element between arrays
  *   - `compareAt` — compare two elements in the same array by index
  *   - `compareAcross` — compare elements across two arrays by index
  *
  * ==Opaque Type Givens==
  *
  * For opaque types backed by primitives, declare givens with the '''concrete subclass''', not the trait:
  * {{{
  * opaque type Pixels = Int
  * object Pixels {
  *   given MkArray.OfInts[Pixels] = MkArray.ofIntAs[Pixels]
  * }
  * }}}
  *
  * This ensures `MkArray.withResolved` dispatches to the specialized path (`OfInts.get([II)I`) rather than the erased bridge (`MkArray.get(Object,I)Object`).
  *
  * @see
  *   [[MkArray.withResolved]] for zero-boxing element access in inline methods
  * @see
  *   [[MkArray.resolve]] for compile-time resolution of the concrete subclass
  */
sealed trait MkArray[A] {

  // --- Array lifecycle (no boxing risk, safe through erased trait) ---

  def create(size:        Int):                               Array[A]
  def copyOf(source:      Array[A], newLength: Int):          Array[A]
  def copyOfRange(source: Array[A], from:      Int, to: Int): Array[A]
  def nullOut(array:      Array[A], index:     Int):          Unit
  def nullOutRange(array: Array[A], from:      Int, to: Int): Unit

  // --- Element access (boxes through trait — use withResolved in inline def for zero-boxing) ---

  def get(array:    Array[A], index: Int):           A
  def set(array:    Array[A], index: Int, value: A): Unit
  def elemEquals(a: A, b:            A):             Boolean

  // --- Type bridging (for use inside inline methods at erased boundaries) ---

  def castIn(value:    Any):      A
  def castArray(array: Array[?]): Array[A]

  // --- Array manipulation by index (no boxing — never extracts A through parameters) ---

  def swap(array:      Array[A], i:    Int, j:   Int):                                        Unit
  def copyElement(src: Array[A], srcI: Int, dst: Array[A], dstI: Int):                        Unit
  def compareAt(array: Array[A], i:    Int, j:   Int, ordering:  Ordering[A]):                Int
  def compareAcross(a: Array[A], ai:   Int, b:   Array[A], bi:   Int, ordering: Ordering[A]): Int
}

object MkArray {

  // ---- Byte ----

  final class OfBytes[A] private[MkArray] () extends MkArray[A & Byte] {
    def create(size: Int):                               Array[A & Byte] = new Array[Byte](size).asInstanceOf[Array[A & Byte]]
    def copyOf(source: Array[A & Byte], newLength: Int): Array[A & Byte] = {
      val s = source.asInstanceOf[Array[Byte]]; val d = new Array[Byte](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Byte]]
    }
    def copyOfRange(source: Array[A & Byte], from: Int, to: Int): Array[A & Byte] = {
      val len = to - from; val d = new Array[Byte](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Byte]]
    }
    def get(array:          Array[A & Byte], index: Int):                                    A & Byte        = array.asInstanceOf[Array[Byte]](index).asInstanceOf[A & Byte]
    def set(array:          Array[A & Byte], index: Int, value: A & Byte):                   Unit            = array.asInstanceOf[Array[Byte]](index) = value.asInstanceOf[Byte]
    def elemEquals(a:       A & Byte, b:            A & Byte):                               Boolean         = a.asInstanceOf[Byte] == b.asInstanceOf[Byte]
    def nullOut(array:      Array[A & Byte], index: Int):                                    Unit            = ()
    def nullOutRange(array: Array[A & Byte], from:  Int, to:    Int):                        Unit            = ()
    def castIn(value:       Any):                                                            A & Byte        = value.asInstanceOf[A & Byte]
    def castArray(array:    Array[?]):                                                       Array[A & Byte] = array.asInstanceOf[Array[A & Byte]]
    def swap(array:         Array[A & Byte], i:     Int, j:     Int):                        Unit            = { val a = array.asInstanceOf[Array[Byte]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Byte], srcI:  Int, dst:   Array[A & Byte], dstI: Int): Unit            = dst.asInstanceOf[Array[Byte]](dstI) = src.asInstanceOf[Array[Byte]](srcI)
    def compareAt(array: Array[A & Byte], i: Int, j: Int, ordering: Ordering[A & Byte]):     Int             =
      ordering.compare(array.asInstanceOf[Array[Byte]](i).asInstanceOf[A & Byte], array.asInstanceOf[Array[Byte]](j).asInstanceOf[A & Byte])
    def compareAcross(a: Array[A & Byte], ai: Int, b: Array[A & Byte], bi: Int, ordering: Ordering[A & Byte]): Int =
      ordering.compare(a.asInstanceOf[Array[Byte]](ai).asInstanceOf[A & Byte], b.asInstanceOf[Array[Byte]](bi).asInstanceOf[A & Byte])
  }
  given ofByte:    OfBytes[Byte] = new OfBytes[Byte]()
  def ofByteAs[A]: OfBytes[A]    = ofByte.asInstanceOf[OfBytes[A]]

  // ---- Short ----

  final class OfShorts[A] private[MkArray] () extends MkArray[A & Short] {
    def create(size: Int):                                Array[A & Short] = new Array[Short](size).asInstanceOf[Array[A & Short]]
    def copyOf(source: Array[A & Short], newLength: Int): Array[A & Short] = {
      val s = source.asInstanceOf[Array[Short]]; val d = new Array[Short](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Short]]
    }
    def copyOfRange(source: Array[A & Short], from: Int, to: Int): Array[A & Short] = {
      val len = to - from; val d = new Array[Short](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Short]]
    }
    def get(array:          Array[A & Short], index: Int):                                     A & Short        = array.asInstanceOf[Array[Short]](index).asInstanceOf[A & Short]
    def set(array:          Array[A & Short], index: Int, value: A & Short):                   Unit             = array.asInstanceOf[Array[Short]](index) = value.asInstanceOf[Short]
    def elemEquals(a:       A & Short, b:            A & Short):                               Boolean          = a.asInstanceOf[Short] == b.asInstanceOf[Short]
    def nullOut(array:      Array[A & Short], index: Int):                                     Unit             = ()
    def nullOutRange(array: Array[A & Short], from:  Int, to:    Int):                         Unit             = ()
    def castIn(value:       Any):                                                              A & Short        = value.asInstanceOf[A & Short]
    def castArray(array:    Array[?]):                                                         Array[A & Short] = array.asInstanceOf[Array[A & Short]]
    def swap(array:         Array[A & Short], i:     Int, j:     Int):                         Unit             = { val a = array.asInstanceOf[Array[Short]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Short], srcI:  Int, dst:   Array[A & Short], dstI: Int): Unit             = dst.asInstanceOf[Array[Short]](dstI) = src.asInstanceOf[Array[Short]](srcI)
    def compareAt(array: Array[A & Short], i: Int, j: Int, ordering: Ordering[A & Short]):     Int              =
      ordering.compare(array.asInstanceOf[Array[Short]](i).asInstanceOf[A & Short], array.asInstanceOf[Array[Short]](j).asInstanceOf[A & Short])
    def compareAcross(a: Array[A & Short], ai: Int, b: Array[A & Short], bi: Int, ordering: Ordering[A & Short]): Int =
      ordering.compare(a.asInstanceOf[Array[Short]](ai).asInstanceOf[A & Short], b.asInstanceOf[Array[Short]](bi).asInstanceOf[A & Short])
  }
  given ofShort:    OfShorts[Short] = new OfShorts[Short]()
  def ofShortAs[A]: OfShorts[A]     = ofShort.asInstanceOf[OfShorts[A]]

  // ---- Char ----

  final class OfChars[A] private[MkArray] () extends MkArray[A & Char] {
    def create(size: Int):                               Array[A & Char] = new Array[Char](size).asInstanceOf[Array[A & Char]]
    def copyOf(source: Array[A & Char], newLength: Int): Array[A & Char] = {
      val s = source.asInstanceOf[Array[Char]]; val d = new Array[Char](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Char]]
    }
    def copyOfRange(source: Array[A & Char], from: Int, to: Int): Array[A & Char] = {
      val len = to - from; val d = new Array[Char](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Char]]
    }
    def get(array:          Array[A & Char], index: Int):                                    A & Char        = array.asInstanceOf[Array[Char]](index).asInstanceOf[A & Char]
    def set(array:          Array[A & Char], index: Int, value: A & Char):                   Unit            = array.asInstanceOf[Array[Char]](index) = value.asInstanceOf[Char]
    def elemEquals(a:       A & Char, b:            A & Char):                               Boolean         = a.asInstanceOf[Char] == b.asInstanceOf[Char]
    def nullOut(array:      Array[A & Char], index: Int):                                    Unit            = ()
    def nullOutRange(array: Array[A & Char], from:  Int, to:    Int):                        Unit            = ()
    def castIn(value:       Any):                                                            A & Char        = value.asInstanceOf[A & Char]
    def castArray(array:    Array[?]):                                                       Array[A & Char] = array.asInstanceOf[Array[A & Char]]
    def swap(array:         Array[A & Char], i:     Int, j:     Int):                        Unit            = { val a = array.asInstanceOf[Array[Char]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Char], srcI:  Int, dst:   Array[A & Char], dstI: Int): Unit            = dst.asInstanceOf[Array[Char]](dstI) = src.asInstanceOf[Array[Char]](srcI)
    def compareAt(array: Array[A & Char], i: Int, j: Int, ordering: Ordering[A & Char]):     Int             =
      ordering.compare(array.asInstanceOf[Array[Char]](i).asInstanceOf[A & Char], array.asInstanceOf[Array[Char]](j).asInstanceOf[A & Char])
    def compareAcross(a: Array[A & Char], ai: Int, b: Array[A & Char], bi: Int, ordering: Ordering[A & Char]): Int =
      ordering.compare(a.asInstanceOf[Array[Char]](ai).asInstanceOf[A & Char], b.asInstanceOf[Array[Char]](bi).asInstanceOf[A & Char])
  }
  given ofChar:    OfChars[Char] = new OfChars[Char]()
  def ofCharAs[A]: OfChars[A]    = ofChar.asInstanceOf[OfChars[A]]

  // ---- Int ----

  final class OfInts[A] private[MkArray] () extends MkArray[A & Int] {
    def create(size: Int):                              Array[A & Int] = new Array[Int](size).asInstanceOf[Array[A & Int]]
    def copyOf(source: Array[A & Int], newLength: Int): Array[A & Int] = {
      val s = source.asInstanceOf[Array[Int]]; val d = new Array[Int](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Int]]
    }
    def copyOfRange(source: Array[A & Int], from: Int, to: Int): Array[A & Int] = {
      val len = to - from; val d = new Array[Int](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Int]]
    }
    def get(array:          Array[A & Int], index: Int):                                   A & Int        = array.asInstanceOf[Array[Int]](index).asInstanceOf[A & Int]
    def set(array:          Array[A & Int], index: Int, value: A & Int):                   Unit           = array.asInstanceOf[Array[Int]](index) = value.asInstanceOf[Int]
    def elemEquals(a:       A & Int, b:            A & Int):                               Boolean        = a.asInstanceOf[Int] == b.asInstanceOf[Int]
    def nullOut(array:      Array[A & Int], index: Int):                                   Unit           = ()
    def nullOutRange(array: Array[A & Int], from:  Int, to:    Int):                       Unit           = ()
    def castIn(value:       Any):                                                          A & Int        = value.asInstanceOf[A & Int]
    def castArray(array:    Array[?]):                                                     Array[A & Int] = array.asInstanceOf[Array[A & Int]]
    def swap(array:         Array[A & Int], i:     Int, j:     Int):                       Unit           = { val a = array.asInstanceOf[Array[Int]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Int], srcI:  Int, dst:   Array[A & Int], dstI: Int): Unit           = dst.asInstanceOf[Array[Int]](dstI) = src.asInstanceOf[Array[Int]](srcI)
    def compareAt(array: Array[A & Int], i: Int, j: Int, ordering: Ordering[A & Int]):     Int            =
      ordering.compare(array.asInstanceOf[Array[Int]](i).asInstanceOf[A & Int], array.asInstanceOf[Array[Int]](j).asInstanceOf[A & Int])
    def compareAcross(a: Array[A & Int], ai: Int, b: Array[A & Int], bi: Int, ordering: Ordering[A & Int]): Int =
      ordering.compare(a.asInstanceOf[Array[Int]](ai).asInstanceOf[A & Int], b.asInstanceOf[Array[Int]](bi).asInstanceOf[A & Int])
  }
  given ofInt:    OfInts[Int] = new OfInts[Int]()
  def ofIntAs[A]: OfInts[A]   = ofInt.asInstanceOf[OfInts[A]]

  // ---- Long ----

  final class OfLongs[A] private[MkArray] () extends MkArray[A & Long] {
    def create(size: Int):                               Array[A & Long] = new Array[Long](size).asInstanceOf[Array[A & Long]]
    def copyOf(source: Array[A & Long], newLength: Int): Array[A & Long] = {
      val s = source.asInstanceOf[Array[Long]]; val d = new Array[Long](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Long]]
    }
    def copyOfRange(source: Array[A & Long], from: Int, to: Int): Array[A & Long] = {
      val len = to - from; val d = new Array[Long](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Long]]
    }
    def get(array:          Array[A & Long], index: Int):                                    A & Long        = array.asInstanceOf[Array[Long]](index).asInstanceOf[A & Long]
    def set(array:          Array[A & Long], index: Int, value: A & Long):                   Unit            = array.asInstanceOf[Array[Long]](index) = value.asInstanceOf[Long]
    def elemEquals(a:       A & Long, b:            A & Long):                               Boolean         = a.asInstanceOf[Long] == b.asInstanceOf[Long]
    def nullOut(array:      Array[A & Long], index: Int):                                    Unit            = ()
    def nullOutRange(array: Array[A & Long], from:  Int, to:    Int):                        Unit            = ()
    def castIn(value:       Any):                                                            A & Long        = value.asInstanceOf[A & Long]
    def castArray(array:    Array[?]):                                                       Array[A & Long] = array.asInstanceOf[Array[A & Long]]
    def swap(array:         Array[A & Long], i:     Int, j:     Int):                        Unit            = { val a = array.asInstanceOf[Array[Long]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Long], srcI:  Int, dst:   Array[A & Long], dstI: Int): Unit            = dst.asInstanceOf[Array[Long]](dstI) = src.asInstanceOf[Array[Long]](srcI)
    def compareAt(array: Array[A & Long], i: Int, j: Int, ordering: Ordering[A & Long]):     Int             =
      ordering.compare(array.asInstanceOf[Array[Long]](i).asInstanceOf[A & Long], array.asInstanceOf[Array[Long]](j).asInstanceOf[A & Long])
    def compareAcross(a: Array[A & Long], ai: Int, b: Array[A & Long], bi: Int, ordering: Ordering[A & Long]): Int =
      ordering.compare(a.asInstanceOf[Array[Long]](ai).asInstanceOf[A & Long], b.asInstanceOf[Array[Long]](bi).asInstanceOf[A & Long])
  }
  given ofLong:    OfLongs[Long] = new OfLongs[Long]()
  def ofLongAs[A]: OfLongs[A]    = ofLong.asInstanceOf[OfLongs[A]]

  // ---- Float ----

  final class OfFloats[A] private[MkArray] () extends MkArray[A & Float] {
    def create(size: Int):                                Array[A & Float] = new Array[Float](size).asInstanceOf[Array[A & Float]]
    def copyOf(source: Array[A & Float], newLength: Int): Array[A & Float] = {
      val s = source.asInstanceOf[Array[Float]]; val d = new Array[Float](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Float]]
    }
    def copyOfRange(source: Array[A & Float], from: Int, to: Int): Array[A & Float] = {
      val len = to - from; val d = new Array[Float](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Float]]
    }
    def get(array:          Array[A & Float], index: Int):                                     A & Float        = array.asInstanceOf[Array[Float]](index).asInstanceOf[A & Float]
    def set(array:          Array[A & Float], index: Int, value: A & Float):                   Unit             = array.asInstanceOf[Array[Float]](index) = value.asInstanceOf[Float]
    def elemEquals(a:       A & Float, b:            A & Float):                               Boolean          = a.asInstanceOf[Float] == b.asInstanceOf[Float]
    def nullOut(array:      Array[A & Float], index: Int):                                     Unit             = ()
    def nullOutRange(array: Array[A & Float], from:  Int, to:    Int):                         Unit             = ()
    def castIn(value:       Any):                                                              A & Float        = value.asInstanceOf[A & Float]
    def castArray(array:    Array[?]):                                                         Array[A & Float] = array.asInstanceOf[Array[A & Float]]
    def swap(array:         Array[A & Float], i:     Int, j:     Int):                         Unit             = { val a = array.asInstanceOf[Array[Float]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Float], srcI:  Int, dst:   Array[A & Float], dstI: Int): Unit             = dst.asInstanceOf[Array[Float]](dstI) = src.asInstanceOf[Array[Float]](srcI)
    def compareAt(array: Array[A & Float], i: Int, j: Int, ordering: Ordering[A & Float]):     Int              =
      ordering.compare(array.asInstanceOf[Array[Float]](i).asInstanceOf[A & Float], array.asInstanceOf[Array[Float]](j).asInstanceOf[A & Float])
    def compareAcross(a: Array[A & Float], ai: Int, b: Array[A & Float], bi: Int, ordering: Ordering[A & Float]): Int =
      ordering.compare(a.asInstanceOf[Array[Float]](ai).asInstanceOf[A & Float], b.asInstanceOf[Array[Float]](bi).asInstanceOf[A & Float])
  }
  given ofFloat:    OfFloats[Float] = new OfFloats[Float]()
  def ofFloatAs[A]: OfFloats[A]     = ofFloat.asInstanceOf[OfFloats[A]]

  // ---- Double ----

  final class OfDoubles[A] private[MkArray] () extends MkArray[A & Double] {
    def create(size: Int):                                 Array[A & Double] = new Array[Double](size).asInstanceOf[Array[A & Double]]
    def copyOf(source: Array[A & Double], newLength: Int): Array[A & Double] = {
      val s = source.asInstanceOf[Array[Double]]; val d = new Array[Double](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Double]]
    }
    def copyOfRange(source: Array[A & Double], from: Int, to: Int): Array[A & Double] = {
      val len = to - from; val d = new Array[Double](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Double]]
    }
    def get(array:          Array[A & Double], index: Int):                                      A & Double        = array.asInstanceOf[Array[Double]](index).asInstanceOf[A & Double]
    def set(array:          Array[A & Double], index: Int, value: A & Double):                   Unit              = array.asInstanceOf[Array[Double]](index) = value.asInstanceOf[Double]
    def elemEquals(a:       A & Double, b:            A & Double):                               Boolean           = a.asInstanceOf[Double] == b.asInstanceOf[Double]
    def nullOut(array:      Array[A & Double], index: Int):                                      Unit              = ()
    def nullOutRange(array: Array[A & Double], from:  Int, to:    Int):                          Unit              = ()
    def castIn(value:       Any):                                                                A & Double        = value.asInstanceOf[A & Double]
    def castArray(array:    Array[?]):                                                           Array[A & Double] = array.asInstanceOf[Array[A & Double]]
    def swap(array:         Array[A & Double], i:     Int, j:     Int):                          Unit              = { val a = array.asInstanceOf[Array[Double]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src:    Array[A & Double], srcI:  Int, dst:   Array[A & Double], dstI: Int): Unit              = dst.asInstanceOf[Array[Double]](dstI) = src.asInstanceOf[Array[Double]](srcI)
    def compareAt(array: Array[A & Double], i: Int, j: Int, ordering: Ordering[A & Double]):     Int               = ordering.compare(
      array.asInstanceOf[Array[Double]](i).asInstanceOf[A & Double],
      array.asInstanceOf[Array[Double]](j).asInstanceOf[A & Double]
    )
    def compareAcross(a: Array[A & Double], ai: Int, b: Array[A & Double], bi: Int, ordering: Ordering[A & Double]): Int =
      ordering.compare(a.asInstanceOf[Array[Double]](ai).asInstanceOf[A & Double], b.asInstanceOf[Array[Double]](bi).asInstanceOf[A & Double])
  }
  given ofDouble:    OfDoubles[Double] = new OfDoubles[Double]()
  def ofDoubleAs[A]: OfDoubles[A]      = ofDouble.asInstanceOf[OfDoubles[A]]

  // ---- Boolean ----

  final class OfBooleans[A] private[MkArray] () extends MkArray[A & Boolean] {
    def create(size: Int):                                  Array[A & Boolean] = new Array[Boolean](size).asInstanceOf[Array[A & Boolean]]
    def copyOf(source: Array[A & Boolean], newLength: Int): Array[A & Boolean] = {
      val s = source.asInstanceOf[Array[Boolean]]; val d = new Array[Boolean](newLength); System.arraycopy(s, 0, d, 0, Math.min(s.length, newLength)); d.asInstanceOf[Array[A & Boolean]]
    }
    def copyOfRange(source: Array[A & Boolean], from: Int, to: Int): Array[A & Boolean] = {
      val len = to - from; val d = new Array[Boolean](len); System.arraycopy(source, from, d, 0, len); d.asInstanceOf[Array[A & Boolean]]
    }
    def get(array:          Array[A & Boolean], index: Int):                     A & Boolean        = array.asInstanceOf[Array[Boolean]](index).asInstanceOf[A & Boolean]
    def set(array:          Array[A & Boolean], index: Int, value: A & Boolean): Unit               = array.asInstanceOf[Array[Boolean]](index) = value.asInstanceOf[Boolean]
    def elemEquals(a:       A & Boolean, b:            A & Boolean):             Boolean            = a.asInstanceOf[Boolean] == b.asInstanceOf[Boolean]
    def nullOut(array:      Array[A & Boolean], index: Int):                     Unit               = ()
    def nullOutRange(array: Array[A & Boolean], from:  Int, to:    Int):         Unit               = ()
    def castIn(value:       Any):                                                A & Boolean        = value.asInstanceOf[A & Boolean]
    def castArray(array:    Array[?]):                                           Array[A & Boolean] = array.asInstanceOf[Array[A & Boolean]]
    def swap(array:         Array[A & Boolean], i:     Int, j:     Int):         Unit               = { val a = array.asInstanceOf[Array[Boolean]]; val t = a(i); a(i) = a(j); a(j) = t }
    def copyElement(src: Array[A & Boolean], srcI: Int, dst: Array[A & Boolean], dstI: Int):   Unit = dst.asInstanceOf[Array[Boolean]](dstI) = src.asInstanceOf[Array[Boolean]](srcI)
    def compareAt(array: Array[A & Boolean], i: Int, j: Int, ordering: Ordering[A & Boolean]): Int  = ordering.compare(
      array.asInstanceOf[Array[Boolean]](i).asInstanceOf[A & Boolean],
      array.asInstanceOf[Array[Boolean]](j).asInstanceOf[A & Boolean]
    )
    def compareAcross(a: Array[A & Boolean], ai: Int, b: Array[A & Boolean], bi: Int, ordering: Ordering[A & Boolean]): Int =
      ordering.compare(a.asInstanceOf[Array[Boolean]](ai).asInstanceOf[A & Boolean], b.asInstanceOf[Array[Boolean]](bi).asInstanceOf[A & Boolean])
  }
  given ofBoolean:    OfBooleans[Boolean] = new OfBooleans[Boolean]()
  def ofBooleanAs[A]: OfBooleans[A]       = ofBoolean.asInstanceOf[OfBooleans[A]]

  // ---- AnyRef ----

  final class OfRefs[A <: AnyRef] private[MkArray] (using ct: ClassTag[A]) extends MkArray[A] {
    def create(size:        Int):                                  Array[A] = new Array[A](size)
    def copyOf(source:      Array[A], newLength: Int):             Array[A] = { val d = new Array[A](newLength); System.arraycopy(source, 0, d, 0, Math.min(source.length, newLength)); d }
    def copyOfRange(source: Array[A], from:      Int, to:    Int): Array[A] = { val len = to - from; val d = new Array[A](len); System.arraycopy(source, from, d, 0, len); d }
    def get(array:          Array[A], index:     Int):             A        = array(index)
    def set(array:          Array[A], index:     Int, value: A):   Unit     = array(index) = value
    def elemEquals(a:       A, b:                A):               Boolean  = a == b
    def nullOut(array:      Array[A], index:     Int):             Unit     = array(index) = null.asInstanceOf[A]
    def nullOutRange(array: Array[A], from:      Int, to:    Int): Unit     = java.util.Arrays.fill(array.asInstanceOf[Array[AnyRef]], from, to, null)
    def castIn(value:       Any):                                  A        = value.asInstanceOf[A]
    def castArray(array:    Array[?]):                             Array[A] = array.asInstanceOf[Array[A]]
    def swap(array:         Array[A], i:         Int, j:     Int): Unit     = { val t = array(i); array(i) = array(j); array(j) = t }
    def copyElement(src: Array[A], srcI: Int, dst: Array[A], dstI: Int):                        Unit = dst(dstI) = src(srcI)
    def compareAt(array: Array[A], i:    Int, j:   Int, ordering:  Ordering[A]):                Int  = ordering.compare(array(i), array(j))
    def compareAcross(a: Array[A], ai:   Int, b:   Array[A], bi:   Int, ordering: Ordering[A]): Int  = ordering.compare(a(ai), b(bi))
  }
  given anyRef[A <: AnyRef: ClassTag]: OfRefs[A] = new OfRefs[A]()

  // ---- Nullable ----

  given ofNullable[A]: MkArray[Nullable[A]] = anyRef[AnyRef](using scala.reflect.classTag[AnyRef]).asInstanceOf[MkArray[Nullable[A]]]

  // ---- Implicit upcasts for opaque types ----
  // OfInts[A] extends MkArray[A & Int], which is MkArray[A] only inside the opaque companion.
  // Outside, invariance blocks the subtype check. These derived givens bridge the gap so
  // summonInline[MkArray[A]] works for opaque types. Derived givens have lower priority
  // than direct given instances, so no ambiguity arises for plain types like Int.

  given upcastBytes[A](using mk:    OfBytes[A]):    MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastShorts[A](using mk:   OfShorts[A]):   MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastChars[A](using mk:    OfChars[A]):    MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastInts[A](using mk:     OfInts[A]):     MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastLongs[A](using mk:    OfLongs[A]):    MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastFloats[A](using mk:   OfFloats[A]):   MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastDoubles[A](using mk:  OfDoubles[A]):  MkArray[A] = mk.asInstanceOf[MkArray[A]]
  given upcastBooleans[A](using mk: OfBooleans[A]): MkArray[A] = mk.asInstanceOf[MkArray[A]]

  // ---- Compile-time resolution ----

  /** Resolve the concrete `MkArray` subclass for `A` at compile time, falling back to `fallback` if no specialized instance is found.
    *
    * Use from non-inline code to get specialized dispatch. From inside `inline def`, prefer [[withResolved]] which preserves the concrete type through the method body.
    */
  transparent inline def resolve[A](inline fallback: MkArray[A]) =
    scala.compiletime.summonFrom {
      case mk: OfBytes[A]    => mk
      case mk: OfShorts[A]   => mk
      case mk: OfChars[A]    => mk
      case mk: OfInts[A]     => mk
      case mk: OfLongs[A]    => mk
      case mk: OfFloats[A]   => mk
      case mk: OfDoubles[A]  => mk
      case mk: OfBooleans[A] => mk
      case mk: OfRefs[A]     => mk
      case _ => fallback
    }

  /** Execute `body` with the concrete `MkArray` subclass for `A`, enabling fully specialized (zero-boxing) element access at call sites.
    *
    * The polymorphic function type `[B, Mk <: MkArray[B]] => Mk => R` preserves the concrete subclass type through summonFrom branches. Inside the body, call `mk0.get(mk0.castArray(items), i)` for
    * specialized element access, and `mk0.get(...).asInstanceOf[A]` to widen back to the collection's type parameter.
    *
    * Example:
    * {{{
    * inline def foreach(inline f: A => Unit): Unit = MkArray.withResolved[A, Unit](mk) {
    *   [B, Mk <: MkArray[B]] => (mk0: Mk) =>
    *     val items = mk0.castArray(_items)
    *     var i = 0
    *     while (i < _size) { f(mk0.get(items, i).asInstanceOf[A]); i += 1 }
    * }
    * }}}
    */
  inline def withResolved[A, R](inline fallback: MkArray[A])(inline body: [B, Mk <: MkArray[B]] => Mk => R): R =
    scala.compiletime.summonFrom {
      case mk: OfBytes[A]    => body[A & Byte, OfBytes[A]](mk)
      case mk: OfShorts[A]   => body[A & Short, OfShorts[A]](mk)
      case mk: OfChars[A]    => body[A & Char, OfChars[A]](mk)
      case mk: OfInts[A]     => body[A & Int, OfInts[A]](mk)
      case mk: OfLongs[A]    => body[A & Long, OfLongs[A]](mk)
      case mk: OfFloats[A]   => body[A & Float, OfFloats[A]](mk)
      case mk: OfDoubles[A]  => body[A & Double, OfDoubles[A]](mk)
      case mk: OfBooleans[A] => body[A & Boolean, OfBooleans[A]](mk)
      case mk: OfRefs[A]     => body[A, MkArray[A]](mk)
      case _ => body[A, MkArray[A]](fallback)
    }
}
