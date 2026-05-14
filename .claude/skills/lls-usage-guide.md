---
name: lls-usage-guide
description: How to correctly use lls MkArray, collections, and ArrayView from downstream projects — patterns for opaque types, avoiding boxing, common mistakes
---

# LLS Usage Guide for Downstream Projects

## MkArray Given Instances for Opaque Types

### The Pattern

For an opaque type backed by a primitive, declare the given with the **concrete MkArray subclass**, not the trait:

```scala
// In your project:
opaque type Button = Int
object Button {
  def apply(v: Int): Button = v

  // CORRECT: use the concrete subclass OfInts[Button] with ofIntAs helper
  given MkArray.OfInts[Button] = MkArray.ofIntAs[Button]
}
```

### Why the Concrete Type Matters

`MkArray` has a sealed hierarchy of final subclasses (`OfInts`, `OfLongs`, `OfFloats`, etc.). Each generates **specialized JVM methods**:

```
OfInts.get(int[], int) → int       // specialized, zero boxing
MkArray.get(Object, int) → Object  // erased bridge, boxes primitives
```

When `MkArray.withResolved` (used by collection iteration methods) sees `OfInts[Button]` in scope, it dispatches to the specialized path. If it only sees `MkArray[Button]` (the trait), it falls through to the erased bridge → boxing.

### What NOT to Do

```scala
// WRONG: upcasts to trait, loses specialization
given MkArray[Button] = MkArray.ofInt.asInstanceOf[MkArray[Button]]

// WRONG: OfInts[Int] not OfInts[Button] — won't match summonFrom for Button
given MkArray.OfInts[Int] = MkArray.ofInt
```

### Quick Reference for All Primitive Types

```scala
opaque type MyByte    = Byte    → given MkArray.OfBytes[MyByte]    = MkArray.ofByteAs[MyByte]
opaque type MyShort   = Short   → given MkArray.OfShorts[MyShort]  = MkArray.ofShortAs[MyShort]
opaque type MyChar    = Char    → given MkArray.OfChars[MyChar]    = MkArray.ofCharAs[MyChar]
opaque type MyInt     = Int     → given MkArray.OfInts[MyInt]      = MkArray.ofIntAs[MyInt]
opaque type MyLong    = Long    → given MkArray.OfLongs[MyLong]    = MkArray.ofLongAs[MyLong]
opaque type MyFloat   = Float   → given MkArray.OfFloats[MyFloat]  = MkArray.ofFloatAs[MyFloat]
opaque type MyDouble  = Double  → given MkArray.OfDoubles[MyDbl]   = MkArray.ofDoubleAs[MyDbl]
opaque type MyBool    = Boolean → given MkArray.OfBooleans[MyBool] = MkArray.ofBooleanAs[MyBool]
```

For reference types (String, case classes, etc.), `MkArray.anyRef[A]` is summoned automatically via `ClassTag` — no manual given needed.

## Collection Factory Methods

Collection factories (`DynamicArray.apply`, `ObjectMap.apply`, etc.) use `summonInline[MkArray[A]]` internally. This finds **any** `MkArray[A]` in scope — including the concrete subclass givens declared above.

```scala
// This works — summonInline finds given MkArray.OfInts[Button] because OfInts[Button] <: MkArray[Button]
val arr = DynamicArray[Button]()
arr.add(Button(1))
arr.add(Button(2))
```

The factory methods only call `create`/`copyOf`/`copyOfRange` — they don't pass primitives through `A`, so there's no boxing even if the given is upcasted to the trait. The specialization matters for **iteration** methods (`foreach`, `exists`, `find`, etc.) which use `MkArray.withResolved`.

## MkArray.withResolved — When and How

### What It Does

`MkArray.withResolved[A, R](fallback)(body)` tries to summon a concrete `MkArray` subclass for `A` at compile time. If found, the body receives the concrete type (e.g., `OfInts[Button]`), enabling specialized method dispatch. If not found, falls back to the stored `mk` field (erased interface dispatch).

### When It's Used Automatically

All collection `inline def` iteration methods already use `withResolved` internally:
- `DynamicArray.foreach`, `exists`, `find`, `count`, `forall`, `indexWhere`
- `ObjectMap.foreachEntry`, `foreachKey`, `foreachValue`
- `ObjectSet.foreach`
- `ArrayMap.foreachEntry`, `foreachKey`, `foreachValue`
- `OrderedMap.foreachEntry`, `foreachKey`, `foreachValue`
- `OrderedSet.foreach`

You don't need to call `withResolved` yourself when using these methods.

### When to Use It Manually

If you write your own `inline def` that iterates over an array with `MkArray`:

```scala
inline def sumAll(da: DynamicArray[Int]): Int =
  MkArray.withResolved[Int, Int](da.mk) { [B, Mk <: MkArray[B]] => (mk0: Mk) =>
    val items = mk0.castArray(da._items)
    var sum = 0
    var i = 0
    while (i < da._size) { sum += mk0.get(items, i).asInstanceOf[Int]; i += 1 }
    sum
  }
```

### The Polymorphic Function Type Pattern

The `[B, Mk <: MkArray[B]] => (mk0: Mk) => ...` syntax is a **polymorphic function type** that preserves the concrete `MkArray` subclass through `summonFrom` branches. Without it, the concrete type gets widened to `MkArray[A]` when assigned to a `val`.

This was discovered empirically: `val mk0 = MkArray.resolve(...)` loses the type, but `withResolved { [B, Mk <: MkArray[B]] => (mk0: Mk) => ... }` keeps it because the body is inside the summonFrom branch.

## Sort API

All `Sort.sort` and `TimSort.sort` methods **require `MkArray[T]`**:

```scala
// Sort a DynamicArray (uses internal mk field)
val da = DynamicArray[Int]()
da.sort()  // uses da.mk internally

// Sort a raw Array — must provide MkArray
Sort.sort(array, MkArray.ofInt, Ordering.Int)
Sort.sort(array, MkArray.ofInt, Ordering.Int, fromIndex, toIndex)

// Sort a DynamicArray with explicit ordering
Sort.sort(da, MkArray.ofInt, Ordering.Int.reverse)
```

There are **no overloads without MkArray** — the old ones were removed because they defaulted to `OfRefs[AnyRef]` which crashes on primitive arrays (`int[]` cannot be cast to `Object[]`).

### MkArray Sort Operations

`MkArray` provides operations that let sort algorithms manipulate arrays **without extracting elements to `A`** (which would box primitives):

- `mk.swap(array, i, j)` — swap two elements in-place
- `mk.copyElement(src, srcI, dst, dstI)` — copy one element between arrays
- `mk.compareAt(array, i, j, ordering)` — compare two elements by index
- `mk.compareAcross(a, ai, b, bi, ordering)` — compare element in array `a` at `ai` against element in array `b` at `bi`

These are used internally by TimSort's merge loops and QuickSelect's partition.

## ArrayView — Zero-Allocation For Comprehensions

### When to Use

Use `arr.leanView` when you need **composite operations** that stdlib would allocate intermediaries for:

```scala
// 12.6x faster than stdlib — eliminates Tuple2 + Iterator allocation
for ((elem, i) <- array.leanView.zipWithIndex) println(s"$i: $elem")

// 2x faster — eliminates intermediate filtered collection
val result = for { x <- array.leanView; if x > 5 } yield x * 2
```

### When NOT to Use

For simple `foreach` or `map` on a single array, stdlib's JIT already optimizes well:

```scala
// These are already fast — leanView adds no benefit
array.foreach(println)
array.map(_ * 2)
```

### ArrayView Requires MkArray for map/flatMap

`map` and `flatMap` use `MkArray` (not `ClassTag`) for output array creation:

```scala
// Works automatically for primitives and standard reference types
val doubled = array.leanView.map(_ * 2)

// For opaque types, the MkArray given must be in scope
val buttons: Array[Button] = ...
val ids = buttons.leanView.map(_.toInt)  // needs MkArray[Int] in scope for output
```

## Common Mistakes

### 1. Using MkArray[A] instead of MkArray.OfInts[A] for opaque types
**Symptom:** Code compiles but `withResolved` falls through to boxing path.
**Fix:** Declare `given MkArray.OfInts[MyType]` not `given MkArray[MyType]`.

### 2. Calling Sort.sort without MkArray on primitive arrays
**Symptom:** `ClassCastException: [I cannot be cast to [Ljava.lang.Object`
**Fix:** Always pass `MkArray` to `Sort.sort`. Use `DynamicArray.sort()` which passes `mk` internally.

### 3. Opaque type invariance and upcast givens
`OfInts[A]` extends `MkArray[A & Int]`, not `MkArray[A]`. Inside the opaque companion where `A = Int`, the intersection reduces and subtyping holds. But **outside** the companion, `MkArray[A & Int]` is NOT `MkArray[A]` due to invariance.

lls solves this with **implicit upcast givens** (`upcastInts`, `upcastFloats`, etc.) that derive `MkArray[A]` from `OfInts[A]` via a zero-cost cast. These derived givens have lower priority than direct given instances, so no ambiguity arises for plain types like `Int`.

This means `summonInline[MkArray[MyOpaqueType]]` works transparently in collection factories (`DynamicArray[MyType]()`, `ObjectMap[K, MyType]()`) without extra imports. **One given is enough** — you do NOT need a separate `given MkArray[MyType]`.

If you still see failures, run `sbt clean compile` — stale incremental compilation can cache stale type info for opaque types.

### 4. Using inline erasedValue match outside inline def
**Symptom:** "not a constant type" error
**Fix:** `inline erasedValue[T] match` only works inside `inline def`. For runtime dispatch, use the stored `mk` field (erased but correct).

### 5. Trying to delegate inline f to stdlib methods
**Symptom:** Type mismatch — `inline f` is not a `Function1`
**Fix:** `inline` parameters are compile-time constructs, not runtime `Function1` objects. They must be used directly in while loops, not passed to stdlib's `foreach`/`map`.
