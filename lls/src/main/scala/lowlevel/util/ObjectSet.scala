/*
 * Ported from libGDX - https://github.com/libgdx/libgdx
 * Original source: com/badlogic/gdx/utils/ObjectSet.java
 * Original authors: Nathan Sweet, Tommy Ettinger
 * Licensed under the Apache License, Version 2.0
 *
 * Migration notes:
 *   Renames: `@Null` -> `Nullable`; Java iterators -> `foreach` method
 *   Convention: `final class` with `MkArray`-based internals; `filled: Array[Boolean]` for occupancy tracking; private constructor with factory methods; `return` -> `boundary`/`break`
 *   Idiom: split packages
 *   Audited: 2026-03-03
 *
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 0
 * Covenant-baseline-loc: 378
 * Covenant-baseline-methods: ObjectSet,_size,add,addAll,addResize,apply,array,clear,contains,create,createWithMk,ensureCapacity,equals,filled,first,foreach,from,get,h,hashCode,i,internalFilled,internalKeyTable,internalMask,internalMk,internalShift,internalThreshold,isEmpty,items,keyTable,len,loadFactor,locateKey,mask,mk,n,nonEmpty,oldCapacity,oldFilled,oldKeyTable,otherFilled,otherKeys,place,remove,resize,shift,shrink,size,summonMkArray,tableSize,threshold,toArray,toString,ts
 * Covenant-source-reference: com/badlogic/gdx/utils/ObjectSet.java
 * Covenant-verified: 2026-04-19
 *
 * upstream-commit: 5bf673e4ecf9b8d8206a1d164d4326e354ace9a5
 */
package lowlevel
package util

import scala.annotation.publicInBinary
import scala.compiletime.summonInline
import scala.util.boundary

/** An unordered set where the keys are objects. Null keys are not allowed. No allocation is done except when growing the table size.
  *
  * This class performs fast contains and remove (typically O(1), worst case O(n) but that is rare in practice). Add may be slightly slower, depending on hash collisions. Hashcodes are rehashed to
  * reduce collisions and the need to resize. Load factors greater than 0.91 greatly increase the chances to resize to the next higher POT size.
  *
  * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci hashing.
  *
  * Uses `filled: Array[Boolean]` for occupancy tracking instead of null-key checks, so the same implementation works uniformly for both reference and primitive key types.
  *
  * @author
  *   Nathan Sweet, Tommy Ettinger (original implementation)
  */
final class ObjectSet[A] private (
  mk0:            MkArray[A],
  keyTable0:      Array[A],
  filled0:        Array[Boolean],
  size0:          Int,
  mask0:          Int,
  shift0:         Int,
  val loadFactor: Float,
  threshold0:     Int
) {

  @publicInBinary private[util] val mk:        MkArray[A]     = mk0
  @publicInBinary private[util] var keyTable:  Array[A]       = keyTable0
  @publicInBinary private[util] var filled:    Array[Boolean] = filled0
  @publicInBinary private[util] var _size:     Int            = size0
  @publicInBinary private[util] var mask:      Int            = mask0
  @publicInBinary private[util] var shift:     Int            = shift0
  @publicInBinary private[util] var threshold: Int            = threshold0

  // --- Core ---

  /** The number of elements in this set. */
  def size: Int = _size

  /** Returns true if the set is empty. */
  def isEmpty: Boolean = _size == 0

  /** Returns true if the set has one or more items. */
  def nonEmpty: Boolean = _size > 0

  // --- Hash table internals ---

  /** Returns an index >= 0 and <= mask for the specified item using Fibonacci hashing. */
  private def place(key: A): Int =
    (key.hashCode().toLong * 0x9e3779b97f4a7c15L >>> shift).toInt

  /** Returns the index of the key if already present, else -(index + 1) for the next empty index. */
  private def locateKey(key: A): Int = boundary {
    var i = place(key)
    while (true) {
      if (!filled(i)) boundary.break(-(i + 1)) // Empty space is available.
      if (mk.elemEquals(mk.get(keyTable, i), key)) boundary.break(i) // Same key was found.
      i = (i + 1) & mask
    }
    -1 // unreachable
  }

  // --- Access ---

  /** Returns true if the key was added to the set or false if it was already in the set. */
  def add(key: A): Boolean = {
    val i = locateKey(key)
    if (i >= 0) false // Existing key was found.
    else {
      val slot = -(i + 1) // Empty space was found.
      mk.set(keyTable, slot, key)
      filled(slot) = true
      _size += 1
      if (_size >= threshold) resize(keyTable.length << 1)
      true
    }
  }

  /** Adds all elements from another ObjectSet. */
  def addAll(other: ObjectSet[A]): Unit = {
    ensureCapacity(other._size)
    val otherKeys   = other.keyTable
    val otherFilled = other.filled
    val len         = otherKeys.length
    var i           = 0
    while (i < len) {
      if (otherFilled(i)) add(other.mk.get(otherKeys, i))
      i += 1
    }
  }

  /** Adds all elements from a DynamicArray. */
  def addAll(array: DynamicArray[? <: A]): Unit = {
    ensureCapacity(array.size)
    val items = array.items
    val n     = array.size
    var i     = 0
    while (i < n) {
      add(items(i).asInstanceOf[A])
      i += 1
    }
  }

  /** Returns true if the key was removed. Uses backward-shift deletion. */
  def remove(key: A): Boolean = {
    var i = locateKey(key)
    if (i < 0) false
    else {
      // Backward-shift deletion
      var next = (i + 1) & mask
      while (filled(next)) {
        val k         = mk.get(keyTable, next)
        val placement = place(k)
        if (((next - placement) & mask) > ((i - placement) & mask)) {
          mk.set(keyTable, i, k)
          filled(i) = true
          i = next
        }
        next = (next + 1) & mask
      }
      filled(i) = false
      // Null the vacated slot to allow GC of the removed key reference
      mk.nullOut(keyTable, i)
      _size -= 1
      true
    }
  }

  /** Returns true if the specified key is in the set. */
  def contains(key: A): Boolean = locateKey(key) >= 0

  /** Returns the stored instance for the given key, or `Nullable.empty` if not present. Useful when the set contains canonical instances and you want to retrieve the stored one.
    */
  def get(key: A): Nullable[A] = {
    val i = locateKey(key)
    if (i < 0) Nullable.empty[A] else Nullable(mk.get(keyTable, i))
  }

  /** Returns the first non-empty element in the backing table. Throws if the set is empty. */
  def first: A = boundary {
    val len = keyTable.length
    var i   = 0
    while (i < len) {
      if (filled(i)) boundary.break(mk.get(keyTable, i))
      i += 1
    }
    throw new IllegalStateException("ObjectSet is empty.")
  }

  // --- Bulk ---

  /** Clears the set. */
  def clear(): Unit =
    if (_size == 0) ()
    else {
      // Null reference-type array to allow GC before resetting size
      mk.nullOutRange(keyTable, 0, keyTable.length)
      _size = 0
      java.util.Arrays.fill(filled, false)
    }

  /** Clears the set and reduces the size of the backing arrays to be the specified capacity / loadFactor, if they are larger.
    */
  def clear(maximumCapacity: Int): Unit = {
    val tableSize = ObjectMap.tableSize(maximumCapacity, loadFactor)
    if (keyTable.length <= tableSize) clear()
    else {
      _size = 0
      resize(tableSize)
    }
  }

  /** Increases the size of the backing array to accommodate the specified number of additional items / loadFactor. */
  def ensureCapacity(additionalCapacity: Int): Unit = {
    val tableSize = ObjectMap.tableSize(_size + additionalCapacity, loadFactor)
    if (keyTable.length < tableSize) resize(tableSize)
  }

  /** Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less. */
  def shrink(maximumCapacity: Int): Unit = {
    if (maximumCapacity < 0) throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity)
    val tableSize = ObjectMap.tableSize(maximumCapacity, loadFactor)
    if (keyTable.length > tableSize) resize(tableSize)
  }

  /** Internal resize. Rehashes all entries into new arrays. */
  private[util] def resize(newSize: Int): Unit = {
    val oldCapacity = keyTable.length
    val oldKeyTable = keyTable
    val oldFilled   = filled

    threshold = (newSize * loadFactor).toInt
    mask = newSize - 1
    shift = java.lang.Long.numberOfLeadingZeros(mask.toLong).toInt

    keyTable = mk.create(newSize)
    filled = new Array[Boolean](newSize)

    if (_size > 0) {
      var i = 0
      while (i < oldCapacity) {
        if (oldFilled(i)) addResize(mk.get(oldKeyTable, i))
        i += 1
      }
    }
  }

  /** Skips checks for existing keys, doesn't increment size. Used during resize. */
  private def addResize(key: A): Unit = boundary {
    var i = place(key)
    while (true) {
      if (!filled(i)) {
        mk.set(keyTable, i, key)
        filled(i) = true
        boundary.break(())
      }
      i = (i + 1) & mask
    }
  }

  // --- Iteration ---
  // Architecture divergence: The original LibGDX ObjectSet uses a mutable Java-style inner-class iterator (ObjectSetIterator)
  // with pooling via Collections.allocateIterators. This port replaces it with a functional foreach method, which is idiomatic
  // Scala, avoids iterator-pool allocation complexity, and eliminates the nested-iterator misuse bugs that the original guards
  // against. All iteration functionality is preserved; only the mechanism differs.

  /** Calls the given function for each element in the set. Iteration order is not guaranteed. */
  inline def foreach(inline f: A => Unit): Unit = MkArray.withResolved[A, Unit](mk) { [B, Mk <: MkArray[B]] => (mk0: Mk) =>
    val keys = mk0.castArray(keyTable)
    val len  = keys.length
    var i    = 0
    while (i < len) {
      if (filled(i)) f(mk0.get(keys, i).asInstanceOf[A])
      i += 1
    }
  }

  /** Returns a new DynamicArray containing all elements in the set. */
  def toArray: DynamicArray[A] = {
    val array = DynamicArray.createWithMk(mk, _size, true)
    val len   = keyTable.length
    var i     = 0
    while (i < len) {
      if (filled(i)) array.add(mk.get(keyTable, i))
      i += 1
    }
    array
  }

  // --- Standard ---

  override def hashCode(): Int = {
    var h   = _size
    val len = keyTable.length
    var i   = 0
    while (i < len) {
      if (filled(i)) h += mk.get(keyTable, i).hashCode()
      i += 1
    }
    h
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: ObjectSet[?] =>
      if (other eq this) true
      else if (other._size != _size) false
      else {
        val otherSet = other.asInstanceOf[ObjectSet[A]]
        val len      = keyTable.length
        var equal    = true
        var i        = 0
        while (i < len && equal) {
          if (filled(i) && !otherSet.contains(mk.get(keyTable, i))) equal = false
          i += 1
        }
        equal
      }
    case _ => false
  }

  /** Returns a string representation using the specified separator between elements. */
  def toString(separator: String): String =
    if (_size == 0) ""
    else {
      val sb    = new StringBuilder()
      val len   = keyTable.length
      var first = true
      var i     = 0
      while (i < len) {
        if (filled(i)) {
          if (!first) sb.append(separator)
          sb.append(mk.get(keyTable, i))
          first = false
        }
        i += 1
      }
      sb.toString()
    }

  override def toString(): String =
    "{" + toString(", ") + "}"

  // --- Internal accessors for OrderedSet ---

  private[util] def internalKeyTable:  Array[A]       = keyTable
  private[util] def internalFilled:    Array[Boolean] = filled
  private[util] def internalMask:      Int            = mask
  private[util] def internalShift:     Int            = shift
  private[util] def internalThreshold: Int            = threshold
  private[util] def internalMk:        MkArray[A]     = mk
}

object ObjectSet {

  /** Creates an ObjectSet with default capacity 51 and load factor 0.8. */
  inline def apply[A](): ObjectSet[A] = apply[A](51, 0.8f)

  /** Creates an ObjectSet with the given capacity and default load factor 0.8. */
  inline def apply[A](capacity: Int): ObjectSet[A] = apply[A](capacity, 0.8f)

  /** Creates an ObjectSet with the given capacity and load factor. */
  inline def apply[A](capacity: Int, loadFactor: Float): ObjectSet[A] = {
    val mk = summonMkArray[A]
    create(mk, capacity, loadFactor)
  }

  /** Creates an ObjectSet that is a copy of the given set. */
  def from[A](other: ObjectSet[A]): ObjectSet[A] =
    new ObjectSet[A](
      other.mk,
      other.mk.copyOf(other.keyTable, other.keyTable.length),
      java.util.Arrays.copyOf(other.filled, other.filled.length),
      other._size,
      other.mask,
      other.shift,
      other.loadFactor,
      other.threshold
    )

  private def create[A](mk: MkArray[A], capacity: Int, loadFactor: Float): ObjectSet[A] = {
    if (loadFactor <= 0f || loadFactor >= 1f)
      throw new IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor)

    val ts        = ObjectMap.tableSize(capacity, loadFactor)
    val threshold = (ts * loadFactor).toInt
    val mask      = ts - 1
    val shift     = java.lang.Long.numberOfLeadingZeros(mask.toLong).toInt

    new ObjectSet[A](
      mk,
      mk.create(ts),
      new Array[Boolean](ts),
      0,
      mask,
      shift,
      loadFactor,
      threshold
    )
  }

  /** Creates an ObjectSet with explicit MkArray instance. For use by OrderedSet and other internal code. */
  private[util] def createWithMk[A](mk: MkArray[A], capacity: Int, loadFactor: Float): ObjectSet[A] =
    create(mk, capacity, loadFactor)

  /** Resolves MkArray at compile time using summonInline. */
  private inline def summonMkArray[A]: MkArray[A] = summonInline[MkArray[A]]
}
