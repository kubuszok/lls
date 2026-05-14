/*
 * Ported from libGDX - https://github.com/libgdx/libgdx
 * Original source: com/badlogic/gdx/utils/ObjectMap.java
 * Original authors: Nathan Sweet, Tommy Ettinger
 * Licensed under the Apache License, Version 2.0
 *
 * Migration notes:
 *   Renames: `@Null` return -> `Nullable`; Java `Entries`/`Keys`/`Values` iterators -> `foreachEntry`/`foreachKey`/`foreachValue`
 *   Convention: `final class` with `MkArray`-based internals; `filled: Array[Boolean]` for occupancy instead of null-key sentinel; private constructor with factory methods; `return` -> `boundary`/`break`
 *   Idiom: split packages
 *   Audited: 2026-03-03
 *
 * Scala port copyright 2025-2026 Mateusz Kubuszok
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 0
 * Covenant-baseline-loc: 504
 * Covenant-baseline-methods: ObjectMap,_size,apply,clear,containsKey,containsValue,create,createWithMk,ensureCapacity,equals,equalsIdentity,filled,findKey,foreachEntry,foreachKey,foreachValue,from,get,getUnsafe,h,hashCode,i,internalFilled,internalKeyTable,internalMask,internalShift,internalThreshold,internalValueTable,isEmpty,keyTable,len,loadFactor,locateKey,map,mask,mkK,mkV,nonEmpty,oldCapacity,oldFilled,oldKeyTable,oldValTable,otherFilled,otherKeys,otherValues,place,put,putAll,putResize,remove,resize,shift,shrink,size,summonMkArray,tableSize,threshold,toString,toStringImpl,ts,valueTable
 * Covenant-source-reference: com/badlogic/gdx/utils/ObjectMap.java
 * Covenant-verified: 2026-04-19
 *
 * upstream-commit: 5bf673e4ecf9b8d8206a1d164d4326e354ace9a5
 */
package lowlevel
package util

import scala.annotation.publicInBinary
import scala.compiletime.summonInline
import scala.util.boundary

/** An unordered map where the keys and values are objects. Null keys are not allowed. No allocation is done except when growing the table size.
  *
  * This class performs fast contains and remove (typically O(1), worst case O(n) but that is rare in practice). Add may be slightly slower, depending on hash collisions. Hashcodes are rehashed to
  * reduce collisions and the need to resize. Load factors greater than 0.91 greatly increase the chances to resize to the next higher POT size.
  *
  * This implementation uses linear probing with the backward shift algorithm for removal. Hashcodes are rehashed using Fibonacci hashing, instead of the more common power-of-two mask, to better
  * distribute poor hashCodes.
  *
  * Uses `filled: Array[Boolean]` for occupancy tracking instead of null-key checks. This means the same implementation works uniformly for both reference and primitive key types (e.g. `Int`, `Long`)
  * without special-casing for key=0.
  *
  * @author
  *   Nathan Sweet, Tommy Ettinger (original implementation)
  */
final class ObjectMap[K, V] private (
  mkK0:           MkArray[K],
  mkV0:           MkArray[V],
  keyTable0:      Array[K],
  valueTable0:    Array[V],
  filled0:        Array[Boolean],
  size0:          Int,
  mask0:          Int,
  shift0:         Int,
  val loadFactor: Float,
  threshold0:     Int
) {

  @publicInBinary private[util] val mkK:        MkArray[K]     = mkK0
  @publicInBinary private[util] val mkV:        MkArray[V]     = mkV0
  @publicInBinary private[util] var keyTable:   Array[K]       = keyTable0
  @publicInBinary private[util] var valueTable: Array[V]       = valueTable0
  @publicInBinary private[util] var filled:     Array[Boolean] = filled0
  @publicInBinary private[util] var _size:      Int            = size0
  @publicInBinary private[util] var mask:       Int            = mask0
  @publicInBinary private[util] var shift:      Int            = shift0
  @publicInBinary private[util] var threshold:  Int            = threshold0

  // --- Core ---

  /** The number of key-value pairs in this map. */
  def size: Int = _size

  /** Returns true if the map is empty. */
  def isEmpty: Boolean = _size == 0

  /** Returns true if the map has one or more items. */
  def nonEmpty: Boolean = _size > 0

  // --- Hash table internals ---

  /** Returns an index >= 0 and <= mask for the specified item using Fibonacci hashing. */
  private def place(key: K): Int =
    (key.hashCode().toLong * 0x9e3779b97f4a7c15L >>> shift).toInt

  /** Returns the index of the key if already present, else -(index + 1) for the next empty index. */
  private def locateKey(key: K): Int = boundary {
    var i = place(key)
    while (true) {
      if (!filled(i)) boundary.break(-(i + 1)) // Empty space is available.
      if (mkK.elemEquals(mkK.get(keyTable, i), key)) boundary.break(i) // Same key was found.
      i = (i + 1) & mask
    }
    -1 // unreachable
  }

  // --- Access ---

  /** Returns the old value associated with the specified key, or `Nullable.empty` if the key was not already in the map.
    */
  def put(key: K, value: V): Nullable[V] = {
    val i = locateKey(key)
    if (i >= 0) { // Existing key was found.
      val oldValue = mkV.get(valueTable, i)
      mkV.set(valueTable, i, value)
      Nullable(oldValue)
    } else {
      val slot = -(i + 1) // Empty space was found.
      mkK.set(keyTable, slot, key)
      mkV.set(valueTable, slot, value)
      filled(slot) = true
      _size += 1
      if (_size >= threshold) resize(keyTable.length << 1)
      Nullable.empty[V]
    }
  }

  /** Returns the value for the specified key, or `Nullable.empty` if the key is not in the map. */
  def get(key: K): Nullable[V] = {
    val i = locateKey(key)
    if (i < 0) Nullable.empty[V] else Nullable(mkV.get(valueTable, i))
  }

  /** Returns the value for the specified key, or the default value if the key is not in the map. */
  def get(key: K, defaultValue: V): V = {
    val i = locateKey(key)
    if (i < 0) defaultValue else mkV.get(valueTable, i)
  }

  /** Returns the value for the specified key without checking if it exists. Only safe when the caller guarantees the key is present in the map.
    */
  private[util] def getUnsafe(key: K): V = {
    val i = locateKey(key)
    mkV.get(valueTable, i)
  }

  /** Returns the value for the removed key, or `Nullable.empty` if the key is not in the map. Uses backward-shift deletion to maintain probe sequences without tombstones.
    */
  def remove(key: K): Nullable[V] = {
    var i = locateKey(key)
    if (i < 0) Nullable.empty[V]
    else {
      val oldValue = mkV.get(valueTable, i)
      // Backward-shift deletion
      var next = (i + 1) & mask
      while (filled(next)) {
        val k         = mkK.get(keyTable, next)
        val placement = place(k)
        if (((next - placement) & mask) > ((i - placement) & mask)) {
          mkK.set(keyTable, i, k)
          mkV.set(valueTable, i, mkV.get(valueTable, next))
          filled(i) = true
          i = next
        }
        next = (next + 1) & mask
      }
      filled(i) = false
      // Null the vacated slot to allow GC of the removed key/value references
      mkK.nullOut(keyTable, i)
      mkV.nullOut(valueTable, i)
      _size -= 1
      Nullable(oldValue)
    }
  }

  /** Returns true if the specified key is in the map. */
  def containsKey(key: K): Boolean = locateKey(key) >= 0

  /** Returns true if the specified value is in the map. Note this traverses the entire map and compares every value, which may be an expensive operation.
    */
  def containsValue(value: V): Boolean = containsValue(value, identity = false)

  /** Returns true if the specified value is in the map. Note this traverses the entire map and compares every value, which may be an expensive operation.
    * @param identity
    *   If true, `eq` (reference identity) comparison will be used. If false, `==` (equals) comparison will be used.
    */
  def containsValue(value: V, identity: Boolean): Boolean = boundary {
    val len = keyTable.length
    var i   = 0
    if (identity) {
      while (i < len) {
        if (filled(i) && (mkV.get(valueTable, i).asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef])) boundary.break(true)
        i += 1
      }
    } else {
      while (i < len) {
        if (filled(i) && mkV.elemEquals(mkV.get(valueTable, i), value)) boundary.break(true)
        i += 1
      }
    }
    false
  }

  /** Returns the key for the specified value, or `Nullable.empty` if it is not in the map. Note this traverses the entire map and compares every value, which may be an expensive operation.
    */
  def findKey(value: V): Nullable[K] = findKey(value, identity = false)

  /** Returns the key for the specified value, or `Nullable.empty` if it is not in the map. Note this traverses the entire map and compares every value, which may be an expensive operation.
    * @param identity
    *   If true, `eq` (reference identity) comparison will be used. If false, `==` (equals) comparison will be used.
    */
  def findKey(value: V, identity: Boolean): Nullable[K] = boundary {
    val len = keyTable.length
    var i   = 0
    if (identity) {
      while (i < len) {
        if (filled(i) && (mkV.get(valueTable, i).asInstanceOf[AnyRef] eq value.asInstanceOf[AnyRef]))
          boundary.break(Nullable(mkK.get(keyTable, i)))
        i += 1
      }
    } else {
      while (i < len) {
        if (filled(i) && mkV.elemEquals(mkV.get(valueTable, i), value)) boundary.break(Nullable(mkK.get(keyTable, i)))
        i += 1
      }
    }
    Nullable.empty[K]
  }

  // --- Bulk ---

  /** Copies all key-value pairs from the other map into this map. */
  def putAll(other: ObjectMap[K, V]): Unit = {
    ensureCapacity(other._size)
    val otherKeys   = other.keyTable
    val otherValues = other.valueTable
    val otherFilled = other.filled
    val len         = otherKeys.length
    var i           = 0
    while (i < len) {
      if (otherFilled(i)) put(other.mkK.get(otherKeys, i), other.mkV.get(otherValues, i))
      i += 1
    }
  }

  /** Clears the map. */
  def clear(): Unit =
    if (_size == 0) ()
    else {
      // Null reference-type arrays to allow GC before resetting size
      mkK.nullOutRange(keyTable, 0, keyTable.length)
      mkV.nullOutRange(valueTable, 0, valueTable.length)
      _size = 0
      java.util.Arrays.fill(filled, false)
    }

  /** Clears the map and reduces the size of the backing arrays to be the specified capacity / loadFactor, if they are larger.
    */
  def clear(maximumCapacity: Int): Unit = {
    val tableSize = ObjectMap.tableSize(maximumCapacity, loadFactor)
    if (keyTable.length <= tableSize) clear()
    else {
      _size = 0
      resize(tableSize)
    }
  }

  /** Increases the size of the backing array to accommodate the specified number of additional items / loadFactor. Useful before adding many items to avoid multiple backing array resizes.
    */
  def ensureCapacity(additionalCapacity: Int): Unit = {
    val tableSize = ObjectMap.tableSize(_size + additionalCapacity, loadFactor)
    if (keyTable.length < tableSize) resize(tableSize)
  }

  /** Reduces the size of the backing arrays to be the specified capacity / loadFactor, or less. If the capacity is already less, nothing is done. If the map contains more items than the specified
    * capacity, the next highest power of two capacity is used instead.
    */
  def shrink(maximumCapacity: Int): Unit = {
    if (maximumCapacity < 0) throw new IllegalArgumentException("maximumCapacity must be >= 0: " + maximumCapacity)
    val tableSize = ObjectMap.tableSize(maximumCapacity, loadFactor)
    if (keyTable.length > tableSize) resize(tableSize)
  }

  /** Internal resize. Rehashes all entries into new arrays. */
  private[util] def resize(newSize: Int): Unit = {
    val oldCapacity = keyTable.length
    val oldKeyTable = keyTable
    val oldValTable = valueTable
    val oldFilled   = filled

    threshold = (newSize * loadFactor).toInt
    mask = newSize - 1
    shift = java.lang.Long.numberOfLeadingZeros(mask.toLong)

    keyTable = mkK.create(newSize)
    valueTable = mkV.create(newSize)
    filled = new Array[Boolean](newSize)

    if (_size > 0) {
      var i = 0
      while (i < oldCapacity) {
        if (oldFilled(i)) putResize(mkK.get(oldKeyTable, i), mkV.get(oldValTable, i))
        i += 1
      }
    }
  }

  /** Skips checks for existing keys, doesn't increment size. Used during resize. */
  private def putResize(key: K, value: V): Unit = boundary {
    var i = place(key)
    while (true) {
      if (!filled(i)) {
        mkK.set(keyTable, i, key)
        mkV.set(valueTable, i, value)
        filled(i) = true
        boundary.break(())
      }
      i = (i + 1) & mask
    }
  }

  // --- Iteration ---
  // Architecture divergence: The original LibGDX ObjectMap uses mutable Java-style inner-class iterators (Entries, Keys, Values)
  // with pooling via Collections.allocateIterators. This port replaces them with functional foreach* methods, which are idiomatic
  // Scala, avoid iterator-pool allocation complexity, and eliminate the nested-iterator misuse bugs that the original guards against.
  // All iteration functionality (entries, keys, values) is preserved; only the mechanism differs.

  /** Calls the given function for each key-value pair in the map. Iteration order is not guaranteed. */
  inline def foreachEntry(inline f: (K, V) => Unit): Unit =
    MkArray.withResolved[K, Unit](mkK) { [BK, MkK <: MkArray[BK]] => (mk0K: MkK) =>
      MkArray.withResolved[V, Unit](mkV) { [BV, MkV <: MkArray[BV]] => (mk0V: MkV) =>
        val keys = mk0K.castArray(keyTable)
        val vals = mk0V.castArray(valueTable)
        val len  = keys.length
        var i    = 0
        while (i < len) {
          if (filled(i)) f(mk0K.get(keys, i).asInstanceOf[K], mk0V.get(vals, i).asInstanceOf[V])
          i += 1
        }
      }
    }

  /** Calls the given function for each key in the map. Iteration order is not guaranteed. */
  inline def foreachKey(inline f: K => Unit): Unit =
    MkArray.withResolved[K, Unit](mkK) { [B, Mk <: MkArray[B]] => (mk0: Mk) =>
      val keys = mk0.castArray(keyTable)
      val len  = keys.length
      var i    = 0
      while (i < len) {
        if (filled(i)) f(mk0.get(keys, i).asInstanceOf[K])
        i += 1
      }
    }

  /** Calls the given function for each value in the map. Iteration order is not guaranteed. */
  inline def foreachValue(inline f: V => Unit): Unit =
    MkArray.withResolved[V, Unit](mkV) { [B, Mk <: MkArray[B]] => (mk0: Mk) =>
      val vals = mk0.castArray(valueTable)
      val len  = vals.length
      var i    = 0
      while (i < len) {
        if (filled(i)) f(mk0.get(vals, i).asInstanceOf[V])
        i += 1
      }
    }

  // --- Standard ---

  override def hashCode(): Int = {
    var h   = _size
    val len = keyTable.length
    var i   = 0
    while (i < len) {
      if (filled(i)) {
        h += mkK.get(keyTable, i).hashCode()
        h += mkV.get(valueTable, i).hashCode()
      }
      i += 1
    }
    h
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: ObjectMap[?, ?] =>
      if (other eq this) true
      else if (other._size != _size) false
      else {
        val otherMap = other.asInstanceOf[ObjectMap[K, V]]
        var equal    = true
        val len      = keyTable.length
        var i        = 0
        while (i < len && equal) {
          if (filled(i)) {
            val value    = mkV.get(valueTable, i)
            val otherVal = otherMap.get(mkK.get(keyTable, i))
            if (otherVal.isEmpty || otherVal.getOrElse(value) != value) equal = false
          }
          i += 1
        }
        equal
      }
    case _ => false
  }

  /** Uses `eq` (reference identity) for comparison of each value. */
  def equalsIdentity(obj: Any): Boolean = obj match {
    case other: ObjectMap[?, ?] =>
      if (other eq this) true
      else if (other._size != _size) false
      else {
        val otherMap = other.asInstanceOf[ObjectMap[K, V]]
        var equal    = true
        val len      = keyTable.length
        var i        = 0
        while (i < len && equal) {
          if (filled(i)) {
            val otherVal = otherMap.get(mkK.get(keyTable, i))
            if (otherVal.isEmpty || !(mkV.get(valueTable, i).asInstanceOf[AnyRef] eq otherVal.get.asInstanceOf[AnyRef]))
              equal = false
          }
          i += 1
        }
        equal
      }
    case _ => false
  }

  /** Returns a string representation using the specified separator between entries. */
  def toString(separator: String): String = toStringImpl(separator, braces = false)

  override def toString(): String = toStringImpl(", ", braces = true)

  /** Internal toString with configurable separator and braces. */
  protected def toStringImpl(separator: String, braces: Boolean): String =
    if (_size == 0) {
      if (braces) "{}" else ""
    } else {
      val sb  = new StringBuilder()
      val len = keyTable.length
      if (braces) sb.append('{')
      var first = true
      var i     = 0
      while (i < len) {
        if (filled(i)) {
          if (!first) sb.append(separator)
          sb.append(mkK.get(keyTable, i))
          sb.append('=')
          sb.append(mkV.get(valueTable, i))
          first = false
        }
        i += 1
      }
      if (braces) sb.append('}')
      sb.toString()
    }

  // --- Internal accessors for OrderedMap ---

  private[util] def internalKeyTable:   Array[K]       = keyTable
  private[util] def internalValueTable: Array[V]       = valueTable
  private[util] def internalFilled:     Array[Boolean] = filled
  private[util] def internalMask:       Int            = mask
  private[util] def internalShift:      Int            = shift
  private[util] def internalThreshold:  Int            = threshold
}

object ObjectMap {

  /** Creates an ObjectMap with default capacity 51 and load factor 0.8. */
  inline def apply[K, V](): ObjectMap[K, V] = apply[K, V](51, 0.8f)

  /** Creates an ObjectMap with the given capacity and default load factor 0.8. */
  inline def apply[K, V](capacity: Int): ObjectMap[K, V] = apply[K, V](capacity, 0.8f)

  /** Creates an ObjectMap with the given capacity and load factor. */
  inline def apply[K, V](capacity: Int, loadFactor: Float): ObjectMap[K, V] = {
    val mkK = summonInline[MkArray[K]]
    val mkV = summonInline[MkArray[V]]
    create(mkK, mkV, capacity, loadFactor)
  }

  /** Creates an ObjectMap that is a copy of the given map. */
  def from[K, V](other: ObjectMap[K, V]): ObjectMap[K, V] = {
    val map = new ObjectMap[K, V](
      other.mkK,
      other.mkV,
      other.mkK.copyOf(other.keyTable, other.keyTable.length),
      other.mkV.copyOf(other.valueTable, other.valueTable.length),
      java.util.Arrays.copyOf(other.filled, other.filled.length),
      other._size,
      other.mask,
      other.shift,
      other.loadFactor,
      other.threshold
    )
    map
  }

  private def create[K, V](mkK: MkArray[K], mkV: MkArray[V], capacity: Int, loadFactor: Float): ObjectMap[K, V] = {
    if (loadFactor <= 0f || loadFactor >= 1f)
      throw new IllegalArgumentException("loadFactor must be > 0 and < 1: " + loadFactor)

    val ts        = tableSize(capacity, loadFactor)
    val threshold = (ts * loadFactor).toInt
    val mask      = ts - 1
    val shift     = java.lang.Long.numberOfLeadingZeros(mask.toLong).toInt

    new ObjectMap[K, V](
      mkK,
      mkV,
      mkK.create(ts),
      mkV.create(ts),
      new Array[Boolean](ts),
      0,
      mask,
      shift,
      loadFactor,
      threshold
    )
  }

  /** Creates an ObjectMap with explicit MkArray instances. For use by OrderedMap and other internal code. */
  private[util] def createWithMk[K, V](
    mkK:        MkArray[K],
    mkV:        MkArray[V],
    capacity:   Int,
    loadFactor: Float
  ): ObjectMap[K, V] = create(mkK, mkV, capacity, loadFactor)

  /** Computes the table size (next power of two) for the given capacity and load factor. */
  private[util] def tableSize(capacity: Int, loadFactor: Float): Int = {
    if (capacity < 0) throw new IllegalArgumentException("capacity must be >= 0: " + capacity)
    val ts = math.MathUtils.nextPowerOfTwo(Math.max(2, Math.ceil(capacity.toDouble / loadFactor).toInt))
    if (ts > (1 << 30)) throw new IllegalArgumentException("The required capacity is too large: " + capacity)
    ts
  }

}
