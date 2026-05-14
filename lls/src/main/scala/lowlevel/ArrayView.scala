package lowlevel

import scala.compiletime.erasedValue

opaque type ArrayView[A, IArr <: Boolean, ZipWithIndex <: Boolean] = Array[A]

object ArrayView {

  type Out[A, IArr <: Boolean] = IArr match {
    case true  => IArray[A]
    case false => Array[A]
  }

  type In[A, ZipWithIndex <: Boolean] = ZipWithIndex match {
    case true  => (A, Int)
    case false => A
  }

  extension [A, IArr <: Boolean, ZipWithIndex <: Boolean](view: ArrayView[A, IArr, ZipWithIndex]) {

    inline def length: Int = view.asInstanceOf[Array[A]].length

    inline def foreach(inline f: In[A, ZipWithIndex] => Unit): Unit = {
      val arr = view.asInstanceOf[Array[A]]
      var i   = 0
      while (i < arr.length) {
        inline erasedValue[ZipWithIndex] match {
          case _: true  => f.asInstanceOf[((A, Int)) => Unit]((arr(i), i))
          case _: false => f.asInstanceOf[A => Unit](arr(i))
        }
        i += 1
      }
    }

    inline def map[B](inline f: In[A, ZipWithIndex] => B)(using inline mkB: MkArray[B]): Out[B, IArr] =
      MkArray.withResolved[B, Out[B, IArr]](mkB) { [BB, Mk <: MkArray[BB]] => (mk0: Mk) =>
        val arr = view.asInstanceOf[Array[A]]
        val out = mk0.castArray(mk0.create(arr.length))
        var i   = 0
        while (i < arr.length) {
          mk0.set(
            out,
            i,
            mk0.castIn(
              inline erasedValue[ZipWithIndex] match {
                case _: true  => f.asInstanceOf[((A, Int)) => B]((arr(i), i))
                case _: false => f.asInstanceOf[A => B](arr(i))
              }
            )
          )
          i += 1
        }
        mk0.castArray(out).asInstanceOf[Out[B, IArr]]
      }

    inline def flatMap[B](inline f: In[A, ZipWithIndex] => Array[B])(using inline mkB: MkArray[B]): Out[B, IArr] =
      MkArray.withResolved[B, Out[B, IArr]](mkB) { [BB, Mk <: MkArray[BB]] => (mk0: Mk) =>
        val arr    = view.asInstanceOf[Array[A]]
        var out    = mk0.castArray(mk0.create(16))
        var outLen = 0
        var i      = 0
        while (i < arr.length) {
          val chunk = inline erasedValue[ZipWithIndex] match {
            case _: true  => f.asInstanceOf[((A, Int)) => Array[B]]((arr(i), i))
            case _: false => f.asInstanceOf[A => Array[B]](arr(i))
          }
          val needed = outLen + chunk.length
          if (needed > out.length) {
            val newOut = mk0.castArray(mk0.create(Math.max(needed, out.length * 2)))
            System.arraycopy(out, 0, newOut, 0, outLen)
            out = newOut
          }
          System.arraycopy(chunk, 0, out, outLen, chunk.length)
          outLen += chunk.length
          i += 1
        }
        mk0.copyOfRange(out, 0, outLen).asInstanceOf[Out[B, IArr]]
      }

    inline def withFilter(inline p: In[A, ZipWithIndex] => Boolean): ArrayFilteredView[A, IArr, ZipWithIndex] =
      ArrayFilteredView.wrap[A, IArr, ZipWithIndex](
        view.asInstanceOf[Array[A]],
        x =>
          inline erasedValue[ZipWithIndex] match {
            case _: true  => p.asInstanceOf[((A, Int)) => Boolean](x.asInstanceOf[(A, Int)])
            case _: false => p.asInstanceOf[A => Boolean](x.asInstanceOf[A])
          }
      )
  }

  extension [A, IArr <: Boolean](view: ArrayView[A, IArr, false]) {
    inline def zipWithIndex: ArrayView[A, IArr, true] = view.asInstanceOf[Array[A]]
  }
}

opaque type ArrayFilteredView[A, IArr <: Boolean, ZipWithIndex <: Boolean] = (Array[A], Any => Boolean)

object ArrayFilteredView {

  private[lowlevel] def wrap[A, IArr <: Boolean, ZipWithIndex <: Boolean](
    arr:  Array[A],
    pred: Any => Boolean
  ): ArrayFilteredView[A, IArr, ZipWithIndex] = (arr, pred)

  extension [A, IArr <: Boolean, ZipWithIndex <: Boolean](view: ArrayFilteredView[A, IArr, ZipWithIndex]) {

    private inline def arr:  Array[A]       = view._1
    private inline def pred: Any => Boolean = view._2

    inline def foreach(inline f: ArrayView.In[A, ZipWithIndex] => Unit): Unit = {
      val a = arr
      val p = pred
      var i = 0
      while (i < a.length) {
        inline erasedValue[ZipWithIndex] match {
          case _: true =>
            val pair = (a(i), i)
            if (p(pair)) f.asInstanceOf[((A, Int)) => Unit](pair)
          case _: false =>
            val elem = a(i)
            if (p(elem)) f.asInstanceOf[A => Unit](elem)
        }
        i += 1
      }
    }

    inline def map[B](inline f: ArrayView.In[A, ZipWithIndex] => B)(using inline mkB: MkArray[B]): ArrayView.Out[B, IArr] =
      MkArray.withResolved[B, ArrayView.Out[B, IArr]](mkB) { [BB, Mk <: MkArray[BB]] => (mk0: Mk) =>
        val a      = arr
        val p      = pred
        var out    = mk0.castArray(mk0.create(Math.max(a.length / 2, 4)))
        var outLen = 0
        var i      = 0
        while (i < a.length) {
          inline erasedValue[ZipWithIndex] match {
            case _: true =>
              val pair = (a(i), i)
              if (p(pair)) {
                if (outLen == out.length) { val n = mk0.castArray(mk0.create(out.length * 2)); System.arraycopy(out, 0, n, 0, outLen); out = n }
                mk0.set(out, outLen, mk0.castIn(f.asInstanceOf[((A, Int)) => B](pair)))
                outLen += 1
              }
            case _: false =>
              val elem = a(i)
              if (p(elem)) {
                if (outLen == out.length) { val n = mk0.castArray(mk0.create(out.length * 2)); System.arraycopy(out, 0, n, 0, outLen); out = n }
                mk0.set(out, outLen, mk0.castIn(f.asInstanceOf[A => B](elem)))
                outLen += 1
              }
          }
          i += 1
        }
        mk0.copyOfRange(out, 0, outLen).asInstanceOf[ArrayView.Out[B, IArr]]
      }

    inline def flatMap[B](inline f: ArrayView.In[A, ZipWithIndex] => Array[B])(using inline mkB: MkArray[B]): ArrayView.Out[B, IArr] =
      MkArray.withResolved[B, ArrayView.Out[B, IArr]](mkB) { [BB, Mk <: MkArray[BB]] => (mk0: Mk) =>
        val a      = arr
        val p      = pred
        var out    = mk0.castArray(mk0.create(16))
        var outLen = 0
        var i      = 0
        while (i < a.length) {
          inline erasedValue[ZipWithIndex] match {
            case _: true =>
              val pair = (a(i), i)
              if (p(pair)) {
                val chunk  = f.asInstanceOf[((A, Int)) => Array[B]](pair)
                val needed = outLen + chunk.length
                if (needed > out.length) { val n = mk0.castArray(mk0.create(Math.max(needed, out.length * 2))); System.arraycopy(out, 0, n, 0, outLen); out = n }
                System.arraycopy(chunk, 0, out, outLen, chunk.length)
                outLen += chunk.length
              }
            case _: false =>
              val elem = a(i)
              if (p(elem)) {
                val chunk  = f.asInstanceOf[A => Array[B]](elem)
                val needed = outLen + chunk.length
                if (needed > out.length) { val n = mk0.castArray(mk0.create(Math.max(needed, out.length * 2))); System.arraycopy(out, 0, n, 0, outLen); out = n }
                System.arraycopy(chunk, 0, out, outLen, chunk.length)
                outLen += chunk.length
              }
          }
          i += 1
        }
        mk0.copyOfRange(out, 0, outLen).asInstanceOf[ArrayView.Out[B, IArr]]
      }

    inline def withFilter(inline p: ArrayView.In[A, ZipWithIndex] => Boolean): ArrayFilteredView[A, IArr, ZipWithIndex] =
      ArrayFilteredView.wrap[A, IArr, ZipWithIndex](
        arr,
        x =>
          pred(x) && {
            inline erasedValue[ZipWithIndex] match {
              case _: true  => p.asInstanceOf[((A, Int)) => Boolean](x.asInstanceOf[(A, Int)])
              case _: false => p.asInstanceOf[A => Boolean](x.asInstanceOf[A])
            }
          }
      )
  }
}

// --- Entry points ---

extension [A](arr: Array[A]) {
  inline def leanView: ArrayView[A, false, false] = arr
}

extension [A](arr: IArray[A]) {
  @scala.annotation.targetName("iArrayLeanView")
  inline def leanView: ArrayView[A, true, false] = arr.asInstanceOf[Array[A]]
}
