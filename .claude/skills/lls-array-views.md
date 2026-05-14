---
name: lls-array-views
description: Zero-allocation Array/IArray views using opaque types, type-level booleans, and inline methods for for-comprehension support
---

# ArrayView — Zero-Allocation Array Iteration

## Design

`ArrayView[A, IArr <: Boolean, ZipWithIndex <: Boolean]` is an opaque type over `Array[A]` that provides `inline` methods for `foreach`, `map`, `flatMap`, `withFilter`, and `zipWithIndex`. Type-level booleans encode the view state:

- `IArr = true` → output is `IArray[B]`, `IArr = false` → output is `Array[B]`  
- `ZipWithIndex = true` → element type is `(A, Int)`, `ZipWithIndex = false` → element type is `A`

All methods use `inline erasedValue[ZipWithIndex] match` to select the right branch at compile time.

## Key Findings

### What works (compile-time wins)
- **zipWithIndex**: 12.6x faster than stdlib (eliminates Tuple2 + Iterator allocation)
- **filter + map**: 2.0x faster (eliminates intermediate filtered collection)
- **All lambdas inlined**: `inline f` pastes the body at the call site, no Function1 allocation
- **Tuples erased**: `(arr(i), i)` in zipWithIndex is optimized away when destructured in for-comprehension

### What doesn't help (JIT already handles)
- **Simple foreach/map**: stdlib's `Array.foreach` and `Array.map` are already well-optimized by the JIT — our while loop is on par but not faster
- **Nested foreach without composition**: JIT inlines stdlib's nested loops too

### Cannot delegate to stdlib
`inline f` parameters are compile-time constructs, not runtime `Function1` objects. You cannot pass them to stdlib methods like `array.foreach(f)`. The while loop is the only option for inline methods.

## ArrayFilteredView

`ArrayFilteredView` is an opaque type over `(Array[A], Any => Boolean)`. Since all its methods are `inline`, the tuple is completely erased — verified 0 Tuple2 allocation in bytecode.

The predicate is stored as `Any => Boolean` because it must survive at runtime (for chained `withFilter`). This is the one allocation that can't be avoided — the filter predicate is a runtime function.

## `MkArray.withResolved` for map/flatMap

`map` and `flatMap` use `MkArray.withResolved` instead of `ClassTag` for output array creation. This gives specialized array creation (`new int[]` for Int) and specialized element writes (`iastore`).

## When to use ArrayView

| Operation | Use leanView? | Why |
|---|---|---|
| `arr.zipWithIndex.foreach` | **Yes** | 12.6x faster, eliminates Tuple2+Iterator |
| `for { x <- arr if p(x) } yield f(x)` | **Yes** | 2x faster, eliminates intermediate collection |
| `arr.foreach(f)` | No benefit | JIT already optimizes |
| `arr.map(f)` | No benefit | JIT already optimizes |
| Nested `for { a <- arr1; b <- arr2 }` side-effecting | Marginal | JIT handles simple nesting well |
