package lowlevel

import scala.reflect.ClassTag

sealed trait MkArray[A] {
  def create(size:        Int):                                  Array[A]
  def copyOf(source:      Array[A], newLength: Int):             Array[A]
  def copyOfRange(source: Array[A], from:      Int, to:    Int): Array[A]
  def get(array:          Array[A], index:     Int):             A
  def set(array:          Array[A], index:     Int, value: A):   Unit
  def elemEquals(a:       A, b:                A):               Boolean
  def nullOut(array:      Array[A], index:     Int):             Unit
  def nullOutRange(array: Array[A], from:      Int, to:    Int): Unit
  def castIn(value:       Any):                                  A
  def castArray(array:    Array[?]):                             Array[A]
}

object MkArray {

  final class OfBytes[A] private[MkArray] () extends MkArray[A & Byte] {
    def create(size: Int):                               Array[A & Byte] = new Array[Byte](size).asInstanceOf[Array[A & Byte]]
    def copyOf(source: Array[A & Byte], newLength: Int): Array[A & Byte] = {
      val s    = source.asInstanceOf[Array[Byte]]
      val dest = new Array[Byte](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Byte]]
    }
    def copyOfRange(source: Array[A & Byte], from: Int, to: Int): Array[A & Byte] = {
      val len  = to - from
      val dest = new Array[Byte](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Byte]]
    }
    def get(array:          Array[A & Byte], index: Int):                  A & Byte        = array.asInstanceOf[Array[Byte]](index).asInstanceOf[A & Byte]
    def set(array:          Array[A & Byte], index: Int, value: A & Byte): Unit            = array.asInstanceOf[Array[Byte]](index) = value.asInstanceOf[Byte]
    def elemEquals(a:       A & Byte, b:            A & Byte):             Boolean         = a.asInstanceOf[Byte] == b.asInstanceOf[Byte]
    def nullOut(array:      Array[A & Byte], index: Int):                  Unit            = ()
    def nullOutRange(array: Array[A & Byte], from:  Int, to:    Int):      Unit            = ()
    def castIn(value:       Any):                                          A & Byte        = value.asInstanceOf[A & Byte]
    def castArray(array:    Array[?]):                                     Array[A & Byte] = array.asInstanceOf[Array[A & Byte]]
  }
  given mkByte: OfBytes[Byte] = new OfBytes[Byte]()

  final class OfShorts[A] private[MkArray] () extends MkArray[A & Short] {
    def create(size: Int):                                Array[A & Short] = new Array[Short](size).asInstanceOf[Array[A & Short]]
    def copyOf(source: Array[A & Short], newLength: Int): Array[A & Short] = {
      val s    = source.asInstanceOf[Array[Short]]
      val dest = new Array[Short](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Short]]
    }
    def copyOfRange(source: Array[A & Short], from: Int, to: Int): Array[A & Short] = {
      val len  = to - from
      val dest = new Array[Short](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Short]]
    }
    def get(array:          Array[A & Short], index: Int):                   A & Short        = array.asInstanceOf[Array[Short]](index).asInstanceOf[A & Short]
    def set(array:          Array[A & Short], index: Int, value: A & Short): Unit             = array.asInstanceOf[Array[Short]](index) = value.asInstanceOf[Short]
    def elemEquals(a:       A & Short, b:            A & Short):             Boolean          = a.asInstanceOf[Short] == b.asInstanceOf[Short]
    def nullOut(array:      Array[A & Short], index: Int):                   Unit             = ()
    def nullOutRange(array: Array[A & Short], from:  Int, to:    Int):       Unit             = ()
    def castIn(value:       Any):                                            A & Short        = value.asInstanceOf[A & Short]
    def castArray(array:    Array[?]):                                       Array[A & Short] = array.asInstanceOf[Array[A & Short]]
  }
  given mkShort: OfShorts[Short] = new OfShorts[Short]()

  final class OfChars[A] private[MkArray] () extends MkArray[A & Char] {
    def create(size: Int):                               Array[A & Char] = new Array[Char](size).asInstanceOf[Array[A & Char]]
    def copyOf(source: Array[A & Char], newLength: Int): Array[A & Char] = {
      val s    = source.asInstanceOf[Array[Char]]
      val dest = new Array[Char](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Char]]
    }
    def copyOfRange(source: Array[A & Char], from: Int, to: Int): Array[A & Char] = {
      val len  = to - from
      val dest = new Array[Char](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Char]]
    }
    def get(array:          Array[A & Char], index: Int):                  A & Char        = array.asInstanceOf[Array[Char]](index).asInstanceOf[A & Char]
    def set(array:          Array[A & Char], index: Int, value: A & Char): Unit            = array.asInstanceOf[Array[Char]](index) = value.asInstanceOf[Char]
    def elemEquals(a:       A & Char, b:            A & Char):             Boolean         = a.asInstanceOf[Char] == b.asInstanceOf[Char]
    def nullOut(array:      Array[A & Char], index: Int):                  Unit            = ()
    def nullOutRange(array: Array[A & Char], from:  Int, to:    Int):      Unit            = ()
    def castIn(value:       Any):                                          A & Char        = value.asInstanceOf[A & Char]
    def castArray(array:    Array[?]):                                     Array[A & Char] = array.asInstanceOf[Array[A & Char]]
  }
  given mkChar: OfChars[Char] = new OfChars[Char]()

  final class OfInts[A] private[MkArray] () extends MkArray[A & Int] {
    def create(size: Int):                              Array[A & Int] = new Array[Int](size).asInstanceOf[Array[A & Int]]
    def copyOf(source: Array[A & Int], newLength: Int): Array[A & Int] = {
      val s    = source.asInstanceOf[Array[Int]]
      val dest = new Array[Int](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Int]]
    }
    def copyOfRange(source: Array[A & Int], from: Int, to: Int): Array[A & Int] = {
      val len  = to - from
      val dest = new Array[Int](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Int]]
    }
    def get(array:          Array[A & Int], index: Int):                 A & Int        = array.asInstanceOf[Array[Int]](index).asInstanceOf[A & Int]
    def set(array:          Array[A & Int], index: Int, value: A & Int): Unit           = array.asInstanceOf[Array[Int]](index) = value.asInstanceOf[Int]
    def elemEquals(a:       A & Int, b:            A & Int):             Boolean        = a.asInstanceOf[Int] == b.asInstanceOf[Int]
    def nullOut(array:      Array[A & Int], index: Int):                 Unit           = ()
    def nullOutRange(array: Array[A & Int], from:  Int, to:    Int):     Unit           = ()
    def castIn(value:       Any):                                        A & Int        = value.asInstanceOf[A & Int]
    def castArray(array:    Array[?]):                                   Array[A & Int] = array.asInstanceOf[Array[A & Int]]
  }
  given mkInt: OfInts[Int] = new OfInts[Int]()

  final class OfLongs[A] private[MkArray] () extends MkArray[A & Long] {
    def create(size: Int):                               Array[A & Long] = new Array[Long](size).asInstanceOf[Array[A & Long]]
    def copyOf(source: Array[A & Long], newLength: Int): Array[A & Long] = {
      val s    = source.asInstanceOf[Array[Long]]
      val dest = new Array[Long](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Long]]
    }
    def copyOfRange(source: Array[A & Long], from: Int, to: Int): Array[A & Long] = {
      val len  = to - from
      val dest = new Array[Long](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Long]]
    }
    def get(array:          Array[A & Long], index: Int):                  A & Long        = array.asInstanceOf[Array[Long]](index).asInstanceOf[A & Long]
    def set(array:          Array[A & Long], index: Int, value: A & Long): Unit            = array.asInstanceOf[Array[Long]](index) = value.asInstanceOf[Long]
    def elemEquals(a:       A & Long, b:            A & Long):             Boolean         = a.asInstanceOf[Long] == b.asInstanceOf[Long]
    def nullOut(array:      Array[A & Long], index: Int):                  Unit            = ()
    def nullOutRange(array: Array[A & Long], from:  Int, to:    Int):      Unit            = ()
    def castIn(value:       Any):                                          A & Long        = value.asInstanceOf[A & Long]
    def castArray(array:    Array[?]):                                     Array[A & Long] = array.asInstanceOf[Array[A & Long]]
  }
  given mkLong: OfLongs[Long] = new OfLongs[Long]()

  final class OfFloats[A] private[MkArray] () extends MkArray[A & Float] {
    def create(size: Int):                                Array[A & Float] = new Array[Float](size).asInstanceOf[Array[A & Float]]
    def copyOf(source: Array[A & Float], newLength: Int): Array[A & Float] = {
      val s    = source.asInstanceOf[Array[Float]]
      val dest = new Array[Float](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Float]]
    }
    def copyOfRange(source: Array[A & Float], from: Int, to: Int): Array[A & Float] = {
      val len  = to - from
      val dest = new Array[Float](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Float]]
    }
    def get(array:          Array[A & Float], index: Int):                   A & Float        = array.asInstanceOf[Array[Float]](index).asInstanceOf[A & Float]
    def set(array:          Array[A & Float], index: Int, value: A & Float): Unit             = array.asInstanceOf[Array[Float]](index) = value.asInstanceOf[Float]
    def elemEquals(a:       A & Float, b:            A & Float):             Boolean          = a.asInstanceOf[Float] == b.asInstanceOf[Float]
    def nullOut(array:      Array[A & Float], index: Int):                   Unit             = ()
    def nullOutRange(array: Array[A & Float], from:  Int, to:    Int):       Unit             = ()
    def castIn(value:       Any):                                            A & Float        = value.asInstanceOf[A & Float]
    def castArray(array:    Array[?]):                                       Array[A & Float] = array.asInstanceOf[Array[A & Float]]
  }
  given mkFloat: OfFloats[Float] = new OfFloats[Float]()

  final class OfDoubles[A] private[MkArray] () extends MkArray[A & Double] {
    def create(size: Int):                                 Array[A & Double] = new Array[Double](size).asInstanceOf[Array[A & Double]]
    def copyOf(source: Array[A & Double], newLength: Int): Array[A & Double] = {
      val s    = source.asInstanceOf[Array[Double]]
      val dest = new Array[Double](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Double]]
    }
    def copyOfRange(source: Array[A & Double], from: Int, to: Int): Array[A & Double] = {
      val len  = to - from
      val dest = new Array[Double](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Double]]
    }
    def get(array:          Array[A & Double], index: Int):                    A & Double        = array.asInstanceOf[Array[Double]](index).asInstanceOf[A & Double]
    def set(array:          Array[A & Double], index: Int, value: A & Double): Unit              = array.asInstanceOf[Array[Double]](index) = value.asInstanceOf[Double]
    def elemEquals(a:       A & Double, b:            A & Double):             Boolean           = a.asInstanceOf[Double] == b.asInstanceOf[Double]
    def nullOut(array:      Array[A & Double], index: Int):                    Unit              = ()
    def nullOutRange(array: Array[A & Double], from:  Int, to:    Int):        Unit              = ()
    def castIn(value:       Any):                                              A & Double        = value.asInstanceOf[A & Double]
    def castArray(array:    Array[?]):                                         Array[A & Double] = array.asInstanceOf[Array[A & Double]]
  }
  given mkDouble: OfDoubles[Double] = new OfDoubles[Double]()

  final class OfBooleans[A] private[MkArray] () extends MkArray[A & Boolean] {
    def create(size: Int):                                  Array[A & Boolean] = new Array[Boolean](size).asInstanceOf[Array[A & Boolean]]
    def copyOf(source: Array[A & Boolean], newLength: Int): Array[A & Boolean] = {
      val s    = source.asInstanceOf[Array[Boolean]]
      val dest = new Array[Boolean](newLength)
      System.arraycopy(s, 0, dest, 0, Math.min(s.length, newLength))
      dest.asInstanceOf[Array[A & Boolean]]
    }
    def copyOfRange(source: Array[A & Boolean], from: Int, to: Int): Array[A & Boolean] = {
      val len  = to - from
      val dest = new Array[Boolean](len)
      System.arraycopy(source, from, dest, 0, len)
      dest.asInstanceOf[Array[A & Boolean]]
    }
    def get(array:          Array[A & Boolean], index: Int):                     A & Boolean        = array.asInstanceOf[Array[Boolean]](index).asInstanceOf[A & Boolean]
    def set(array:          Array[A & Boolean], index: Int, value: A & Boolean): Unit               = array.asInstanceOf[Array[Boolean]](index) = value.asInstanceOf[Boolean]
    def elemEquals(a:       A & Boolean, b:            A & Boolean):             Boolean            = a.asInstanceOf[Boolean] == b.asInstanceOf[Boolean]
    def nullOut(array:      Array[A & Boolean], index: Int):                     Unit               = ()
    def nullOutRange(array: Array[A & Boolean], from:  Int, to:    Int):         Unit               = ()
    def castIn(value:       Any):                                                A & Boolean        = value.asInstanceOf[A & Boolean]
    def castArray(array:    Array[?]):                                           Array[A & Boolean] = array.asInstanceOf[Array[A & Boolean]]
  }
  given mkBoolean: OfBooleans[Boolean] = new OfBooleans[Boolean]()

  final class OfRefs[A <: AnyRef] private[MkArray] (using ct: ClassTag[A]) extends MkArray[A] {
    def create(size: Int):                        Array[A] = new Array[A](size)
    def copyOf(source: Array[A], newLength: Int): Array[A] = {
      val dest = new Array[A](newLength)
      System.arraycopy(source, 0, dest, 0, Math.min(source.length, newLength))
      dest
    }
    def copyOfRange(source: Array[A], from: Int, to: Int): Array[A] = {
      val len  = to - from
      val dest = new Array[A](len)
      System.arraycopy(source, from, dest, 0, len)
      dest
    }
    def get(array:          Array[A], index: Int):             A        = array(index)
    def set(array:          Array[A], index: Int, value: A):   Unit     = array(index) = value
    def elemEquals(a:       A, b:            A):               Boolean  = a == b
    def nullOut(array:      Array[A], index: Int):             Unit     = array(index) = null.asInstanceOf[A]
    def nullOutRange(array: Array[A], from:  Int, to:    Int): Unit     = java.util.Arrays.fill(array.asInstanceOf[Array[AnyRef]], from, to, null)
    def castIn(value:       Any):                              A        = value.asInstanceOf[A]
    def castArray(array:    Array[?]):                         Array[A] = array.asInstanceOf[Array[A]]
  }
  given anyRef[A <: AnyRef: ClassTag]: OfRefs[A] = new OfRefs[A]()

  given mkNullable[A]: MkArray[Nullable[A]] = anyRef[AnyRef](using scala.reflect.classTag[AnyRef]).asInstanceOf[MkArray[Nullable[A]]]

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
}
