---
name: lls-unboxed-collections
description: Patterns for eliminating boxing in generic Scala 3 collections using MkArray sealed hierarchy, inline methods, summonFrom, and polymorphic function types
---

# Unboxed Generic Collections in Scala 3 — Lessons Learned

## Problem

Generic collections like `DynamicArray[A]` erase `Array[A]` to `Object` at the JVM level.
Every element access goes through `ScalaRunTime.array_apply` (reflective, always boxes primitives).
Method parameters/returns of type `A` erase to `Object`, causing boxing at every call boundary.

## Architecture: Sealed MkArray Hierarchy

### The Pattern
```scala
sealed trait MkArray[A] {
  def get(array: Array[A], index: Int): A
  def set(array: Array[A], index: Int, value: A): Unit
  def castArray(array: Array[?]): Array[A]
  def castIn(value: Any): A
  // ... other methods
}
object MkArray {
  final class OfInts[A] private[MkArray] () extends MkArray[A & Int] { ... }
  given mkInt: OfInts[Int] = new OfInts[Int]()
  // ... OfBytes, OfShorts, OfChars, OfLongs, OfFloats, OfDoubles, OfBooleans, OfRefs
}
```

### Why It Works
- `final class OfInts` generates **two** JVM method signatures:
  - Specialized: `int get(int[], int)` — direct `iaload`, zero boxing
  - Bridge: `Object get(Object, int)` — erased, for interface compatibility
- When the call site knows the concrete type is `OfInts`, it uses `invokevirtual OfInts.get:([II)I` (specialized)
- When only `MkArray[A]` is known, it uses `invokeinterface MkArray.get:(Object,I)Object` (erased)

### The `A & Prim` Intersection Type
- `OfInts[A]` extends `MkArray[A & Int]`, not `MkArray[Int]`
- This allows reuse for opaque types backed by primitives (e.g., `opaque type Pixels = Int`)
- Methods use `asInstanceOf` to bridge between `A & Int` and the concrete primitive — these are no-ops when `A = Int`

### castIn / castArray Methods
- `castIn(value: Any): A` — narrows `Any` to `A & Prim` (contravariant position, works with JVM overriding)
- `castArray(array: Array[?]): Array[A]` — narrows erased `Object` to typed `Array[A & Int]`
- These let callers bridge between the erased field types (`Array[A]`) and the specialized method signatures
- Do NOT put `castOut` — widening the return type conflicts with JVM method overriding rules

## Three Layers of Boxing Elimination

### Layer 1: Virtual dispatch through MkArray (non-inline methods)
Collections store `val mk: MkArray[A]` and call `mk.get(_items, i)` instead of `_items(i)`.
This eliminates `ScalaRunTime.array_apply` (reflection) but still goes through `invokeinterface` (erased return type, needs `unboxToInt`).
**Used for:** all non-inline methods (add, remove, apply, update, etc.)

### Layer 2: Inline predicates (eliminates Function1 boxing)
```scala
inline def foreach(inline f: A => Unit): Unit = { ... f(mk.get(_items, i)) ... }
```
The lambda body is pasted directly at the call site — no `Function1` allocation, no `unboxToBoolean`.
**Requires:** `@publicInBinary private[util]` on fields accessed by inline methods (`mk`, `_items`, `_size`).
**Note:** `@publicInBinary` cannot go on constructor `var` params — move them to body fields:
```scala
final class DynamicArray[A] private (mk0: MkArray[A], items0: Array[A], size0: Int, val preserveOrder: Boolean) {
  @publicInBinary private[util] val mk: MkArray[A] = mk0
  @publicInBinary private[util] var _items: Array[A] = items0
  @publicInBinary private[util] var _size: Int = size0
```

### Layer 3: withResolved — fully specialized element access (eliminates ALL boxing)
```scala
// In MkArray companion:
inline def withResolved[A, R](inline fallback: MkArray[A])(inline body: [B, Mk <: MkArray[B]] => Mk => R): R =
  summonFrom {
    case mk: OfInts[A]  => body[A & Int, OfInts[A]](mk)
    case mk: OfLongs[A] => body[A & Long, OfLongs[A]](mk)
    // ... all primitives
    case mk: OfRefs[A]  => body[A, MkArray[A]](mk)
    case _               => body[A, MkArray[A]](fallback)
  }

// Usage in DynamicArray:
inline def foreach(inline f: A => Unit): Unit = MkArray.withResolved[A, Unit](mk) {
  [B, Mk <: MkArray[B]] => (mk0: Mk) =>
    val items = mk0.castArray(_items)
    var i = 0
    while (i < _size) { f(mk0.get(items, i).asInstanceOf[A]); i += 1 }
}
```
**Result:** `OfInts.get:([II)I` at the call site — direct int[] access, zero boxing end-to-end.

## Critical Gotchas

### transparent inline val widening
`transparent inline def resolve` returns the concrete type (e.g., `OfInts[Int]`), but assigning to `val mk0` inside another `inline def` **widens the type to `MkArray[A]`**. This is a Scala 3 compiler behavior (not a bug per se — the val type is inferred as the LUB of all summonFrom branches when the outer method's type parameter is still abstract).

**Workaround:** Use `withResolved` with polymorphic function types (`[B, Mk <: MkArray[B]] => Mk => R`). The concrete type stays in scope because the body is inside the summonFrom branch, not after a val assignment.

### summonFrom DOES resolve correctly inside nested inline
We verified that `summonFrom { case _: MkArray.OfInts[A] => () }` correctly finds `OfInts[Int]` when `foreach` is inlined at a `DynamicArray[Int]` call site. The issue is purely about type preservation through val bindings, not about given resolution.

### scalafmt and inline summonFrom
`inline summonFrom { ... }` (with `inline` keyword before the call) is NOT valid Scala 3 syntax — `inline` before expressions is only for `if` and `match`. `summonFrom` is already `transparent inline def` in the stdlib. Scalafmt 3.10.7 will error on `inline summonFrom`.

### boundary/break incompatible with inline
`boundary { ... boundary.break(x) ... }` cannot be used inside `inline def` bodies. Replace with flag-based loops:
```scala
// Before (non-inline):
def exists(p: A => Boolean): Boolean = boundary {
  var i = 0; while (i < _size) { if (p(items(i))) boundary.break(true); i += 1 }; false
}
// After (inline):
inline def exists(inline p: A => Boolean): Boolean = {
  var i = 0; var found = false
  while (i < _size && !found) { if (p(...)) found = true; i += 1 }; found
}
```

### Scala Native stale .nir files
Changing MkArray from `trait` to `sealed trait` or changing given return types requires `sbt clean` before testing Scala Native. Stale `.nir` files cause "Unreachable symbols" linking errors.

## When to Apply Each Layer

| Method characteristic | Layer to use |
|---|---|
| No `A` in params/returns (clear, shrink, ensureCapacity) | Layer 1 only (no inline needed) |
| `A` in params/returns but no lambda (apply, add, remove) | Layer 1 (inline would bloat call sites for minimal gain) |
| Takes `A => Boolean` or `A => Unit` lambda | Layer 2 + 3 (`inline def` + `withResolved`) |
| Array bulk ops (System.arraycopy, copyOf, copyOfRange) | No inline needed (operates on erased Object arrays) |

## Verifying Changes

1. **Bytecode:** `javap -c -p <class>.class | grep -E '(invokevirtual|invokeinterface|unbox|BoxesRunTime)'`
   - `invokevirtual OfInts.get:([II)I` = specialized (good)
   - `invokeinterface MkArray.get:(Object,I)Object` + `unboxToInt` = erased (Layer 1 only)
2. **Tests:** `sbt clean test` — always clean build after MkArray changes, run all 3 platforms
3. **Benchmarks:** `sbt 'lls-bench/Jmh/run -wi 3 -i 3 -f 1 -r 1 -w 1 DynamicArrayBench'`
4. **Boxing count:** `javap -c -p <class>.class | grep -c 'ScalaRunTime\|BoxesRunTime'`
