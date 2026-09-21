package lowlevel.port

import balticporter.tir.Reason
import balticporter.transform.AddMembersTransform
import balticporter.transform.AddMembersTransform.MemberSpec

/** The API lls ADDS to the ported collections: members and factories generated from one template per kind (array / map / set). The templates are public so a port of libGDX's sibling collections can
  * give them the same members.
  */
object LlsEnrich:

  private val Pkg = "com.badlogic.gdx.utils"

  def spec(owner: String, name: String, arity: Int, src: String, why: String, static: Boolean = false): (String, MemberSpec) =
    s"$Pkg.$owner" -> MemberSpec(name, arity, src, Reason.Configured("add-members", s"$Pkg.$owner#$name"), Some(why), static)

  // ---------------------------------------------------------------------------------------------
  // KIND 1 — array-like. `items`/`size`/`get`/`set`/`add` are the emitted surface every one of
  // libGDX's eight resizable arrays shares; the template is written once against it.
  // ---------------------------------------------------------------------------------------------

  /** @param owner
    *   upstream simple name @param self emitted type, applied @param elem element type
    * @param tparams
    *   type-parameter clause of the added members (empty for the primitive arrays)
    * @param removeOne
    *   `removeValue` at arity 1 exists @param removeMany `removeAll` at arity 1
    */
  case class ArrayKind(
    owner:      String,
    self:       String,
    elem:       String,
    tparams:    String = "",
    removeOne:  String = "",
    removeMany: String = "",
    /** the FACTORY's type-class clause when the `witness` step is on; the emitted constructors take one and a factory must supply it.
      */
    mk: String = ""
  )

  def arrayMembers(k: ArrayKind): List[(String, MemberSpec)] =
    val E   = k.elem
    val why = "lls collection API on the emitted array surface"
    // How an INLINE member reads the backing array. Its body is expanded where the element type is
    // known, so `this.items` typed `Array[T]` becomes `Array[String]` there and the JVM casts the
    // whole store — which is an `Object[]` for an array built with the reference type class
    // (measured: 15 sge tests, `Object[] cannot be cast to String[]`). Three cases:
    //   * a primitive array kind (no type parameter): the array type is concrete, read it directly;
    //   * generic with the array type class: resolve it at the call site, as the hand-written lls
    //     did — a primitive element is read unboxed, a reference one through `Array[AnyRef]`;
    //   * generic without it: `Array[AnyRef]`, each element narrowed.
    // `loop(result)(body)` yields the member's body; `body` sees the element as `elem` and the index as `i`.
    def loop(resultType: String, before: String, go: String, body: String, result: String): String =
      if k.tparams.isEmpty then s"{ $before val items = this.items; var i: scala.Int = 0; while (i < this.size && $go) { val elem: $E = items(i); $body; i += 1 }; $result }"
      else if k.mk.nonEmpty then
        // No local typed as an array: where the element type is NOT known at the call site (generic
        // code) the type class resolves to the reference one statically, and a local typed
        // `Array[AnyRef]` would cast a primitive store. Untyped, the array only ever reaches the
        // type class's own `get`, which knows what it holds.
        s"lowlevel.MkArray.withResolved[$E, $resultType](scala.Predef.summon[lowlevel.MkArray[$E]]) { [B, Mk <: lowlevel.MkArray[B]] => (mk0: Mk) => " +
          s"{ $before val raw: scala.Any = this.items; var i: scala.Int = 0; while (i < this.size && $go) { " +
          s"val elem: $E = mk0.get(mk0.castArray(raw.asInstanceOf[scala.Array[?]]), i).asInstanceOf[$E]; $body; i += 1 }; $result } }"
      else
        s"{ $before val items = this.items.asInstanceOf[scala.Array[scala.AnyRef]]; var i: scala.Int = 0; while (i < this.size && $go) { val elem: $E = items(i).asInstanceOf[$E]; $body; i += 1 }; $result }"
    val common = List(
      ("apply", 1, s"def apply(index: scala.Int): $E = this.get(index)"),
      ("update", 2, s"def update(index: scala.Int, value: $E): scala.Unit = this.set(index, value)"),
      ("nonEmpty", 0, "def nonEmpty: scala.Boolean = this.size != 0"),
      ("last", 0, s"def last: $E = { if (this.size == 0) { throw new java.lang.IndexOutOfBoundsException(\"Array is empty.\") } else () ; this.items(this.size - 1) }"),
      // the higher-order members are `inline` with an `inline` function parameter, as the hand-written lls had
      // them: the loop is expanded at the call site, so no function object is created and — the element type
      // being known there — the array is read without boxing (lls's README states both)
      ("foreach", 1, s"inline def foreach(inline f: $E => scala.Unit): scala.Unit = ${loop("scala.Unit", "", "true", "f(elem)", "()")}"),
      ("indexWhere", 1, s"inline def indexWhere(inline p: $E => scala.Boolean): scala.Int = ${loop("scala.Int", "var r: scala.Int = -1;", "r < 0", "if (p(elem)) { r = i } else ()", "r")}"),
      ("exists", 1, s"inline def exists(inline p: $E => scala.Boolean): scala.Boolean = this.indexWhere(p) >= 0"),
      ("forall", 1, s"inline def forall(inline p: $E => scala.Boolean): scala.Boolean = this.indexWhere((x: $E) => !p(x)) < 0"),
      // the found element through `get`, a plain method: not another read of the array in an inline body
      ("find",
       1,
       s"inline def find(inline p: $E => scala.Boolean): lowlevel.Nullable[$E] = { val at: scala.Int = this.indexWhere(p); if (at < 0) lowlevel.Nullable.empty[$E] else lowlevel.Nullable(this.get(at)) }"
      ),
      ("count", 1, s"inline def count(inline p: $E => scala.Boolean): scala.Int = ${loop("scala.Int", "var c: scala.Int = 0;", "true", "if (p(elem)) { c += 1 } else ()", "c")}"),
      ("$plus$eq", 1, s"@scala.annotation.targetName(\"plusEquals\") def +=(value: $E): scala.Unit = this.add(value)")
    )
    val removes =
      (if k.removeOne.isEmpty then Nil
       else
         List(
           ("$minus$eq", 1, s"@scala.annotation.targetName(\"minusEquals\") def -=(value: $E): scala.Unit = { ${k.removeOne}; () }")
         )
      ) ++
        (if k.removeMany.isEmpty then Nil
         else
           List(
             ("$minus$minus$eq", 1, s"@scala.annotation.targetName(\"minusMinusEquals\") def --=(other: ${k.self}): scala.Unit = { ${k.removeMany}; () }")
           ))
    // java's own public constructors STAY; these are additions beside them (the maintainer keeps
    // java's shape, so no private constructor and no `MkArray` mint site).
    val factories = List(
      ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
      ("apply", 1, s"def apply${k.tparams}(capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(capacity)"),
      ("apply", 2, s"def apply${k.tparams}(ordered: scala.Boolean, capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(ordered, capacity)"),
      ("from", 1, s"def from${k.tparams}(values: scala.Array[$E])${k.mk}: ${k.self} = new ${k.self}(values)")
    )
    (common ++ removes).map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  /** `Array` alone carries java's `identity` FLAG on nine members. lls spelled the two settings as two names; both are ADDITIONS — java's flag-taking members stay.
    */
  private def refArrayExtras(w: Boolean): List[(String, MemberSpec)] =
    val S   = "lowlevel.util.DynamicArray[? <: T]"
    val why = "lls's flag-free / ByRef pair for java's `identity` argument"
    // With the array type class in scope the four value-equality lookups compare THROUGH it, as the
    // hand-written lls did: java's body reads the element as an object and calls `equals`, which on a
    // primitive-backed array boxes every element (measured 2.5x on `contains` over an `Int` array).
    // Same answers as java's: `value.equals(element)` in java's order, null equal only to null, the
    // first match from the front (`indexOf`, `removeValue`) or from the back (`lastIndexOf`).
    def scanFor(from: String, go: String, step: String) =
      s"scala.util.boundary { val mk = scala.Predef.summon[lowlevel.MkArray[T]]; val items = this.items; var i: scala.Int = $from; " +
        s"while ($go) { if (mk.elemEquals(value, mk.get(items, i))) { scala.util.boundary.break(i) } else () ; i $step 1 }; -1 }"
    val viaTypeClass: Map[String, String] =
      if !w then Map.empty
      else
        Map(
          "indexOf" -> s"def indexOf(value: T): scala.Int = ${scanFor("0", "i < this.size", "+=")}",
          "lastIndexOf" -> s"def lastIndexOf(value: T): scala.Int = ${scanFor("this.size - 1", "i >= 0", "-=")}",
          "contains" -> "def contains(value: T): scala.Boolean = this.indexOf(value) >= 0",
          "removeValue" -> "def removeValue(value: T): scala.Boolean = { val i: scala.Int = this.indexOf(value); if (i < 0) { false } else { this.removeIndex(i); true } }"
        )
    val pairs = List(
      ("contains", 1, (id: String) => s"(value: T): scala.Boolean = this.contains(lowlevel.Nullable(value), $id)"),
      ("containsAll", 1, (id: String) => s"(values: $S): scala.Boolean = this.containsAll(values, $id)"),
      ("containsAny", 1, (id: String) => s"(values: $S): scala.Boolean = this.containsAny(values, $id)"),
      ("indexOf", 1, (id: String) => s"(value: T): scala.Int = this.indexOf(lowlevel.Nullable(value), $id)"),
      ("lastIndexOf", 1, (id: String) => s"(value: T): scala.Int = this.lastIndexOf(lowlevel.Nullable(value), $id)"),
      ("removeValue", 1, (id: String) => s"(value: T): scala.Boolean = this.removeValue(lowlevel.Nullable(value), $id)"),
      ("removeAll", 1, (id: String) => s"(array: $S): scala.Boolean = this.removeAll(array, $id)"),
      ("replaceFirst", 2, (id: String) => s"(value: T, replacement: T): scala.Boolean = this.replaceFirst(lowlevel.Nullable(value), $id, replacement)"),
      ("replaceAll", 2, (id: String) => s"(value: T, replacement: T): scala.Int = this.replaceAll(lowlevel.Nullable(value), $id, lowlevel.Nullable(replacement))")
    )
    pairs.flatMap { (name, arity, body) =>
      List(
        spec("Array", name, arity, viaTypeClass.getOrElse(name, s"def $name${body("false")}"), why),
        spec("Array", name + "ByRef", arity, s"def ${name}ByRef${body("true")}", why)
      )
    } :+ spec(
      "Array",
      "preserveOrder",
      0,
      "def preserveOrder: scala.Boolean = this.ordered",
      "lls's name for java's `ordered` flag, added beside it"
    )

  // ---------------------------------------------------------------------------------------------
  // KIND 2 — map-like. The three iterator accessors (`keys`/`values`/`entries`) are the emitted
  // surface; a SUBCLASS that overrides them (OrderedMap) inherits the right traversal order.
  // ---------------------------------------------------------------------------------------------

  /** @param entryValue
    *   the `Entry.value` type as emitted (`Nullable[V]` on an object-valued map)
    * @param wrap
    *   how a plain value reaches `put` @param getOne `get` at arity 1, if there is one
    * @param removeOne
    *   `remove`/`removeKey` at arity 1, if there is one
    */
  case class MapKind(
    owner:      String,
    key:        String,
    value:      String,
    entryValue: String,
    tparams:    String = "",
    self:       String = "",
    wrap:       String => String = identity,
    /** the entry's stored value back to `V` (`x.orNull` where storage is `Nullable`). */
    unwrap:       String => String = identity,
    getOne:       String = "",
    removeOne:    String = "",
    indexed:      Boolean = false,
    capacityCtor: Boolean = false,
    /** see [[ArrayKind.mk]]. */
    mk: String = "",
    /** the storage is `ObjectMap`'s own — reference `keyTable`/`valueTable` with `null` for an empty slot, `OrderedMap` its ordered subclass — so the traversals may loop over it directly; every other
      * hash family keeps the iterator walk.
      */
    objectTable: Boolean = false
  )

  def mapMembers(k: MapKind): List[(String, MemberSpec)] =
    // three separate vals, not a tuple pattern: an UPPERCASE name on the left of a pattern
    // definition is a constant pattern, and `val (K, V, EV) = …` binds nothing.
    val K   = k.key
    val V   = k.value
    val EV  = k.entryValue
    val why = "lls collection API on the emitted map surface"
    // The traversals loop over the BACKING ARRAYS, as the hand-written lls did — through an iterator
    // they measured 2x-34x slower (the ordered map's iterator does a hash lookup per key, and a
    // generic array read boxes a primitive value). The function receives the RAW stored value, null
    // included, never the `Nullable` storage.
    //   * indexed (`ArrayMap`): both arrays read through the array type class RESOLVED AT THE CALL
    //     SITE (`MkArray.withResolved`), where the key and value types are known — no boxing.
    //   * hash table: the key/value tables in slot order, which is the iterator's own order; an
    //     `OrderedMap` is walked in insertion order through `orderedKeys`.
    def walk(useValue: Boolean, call: (String, String) => String): String =
      if k.indexed && k.mk.isEmpty then
        // no type class in scope (the element-witness step is off): the accessors are all there is
        s"{ var i: scala.Int = 0; while (i < this.size) { ${call("this.getKeyAt(i)", "this.getValueAt(i)")}; i += 1 } }"
      else if k.indexed then
        val mkOf  = (t: String) => s"scala.Predef.summon[lowlevel.MkArray[$t]]"
        val inner =
          // untyped locals, for the reason given at the array kind: generic code must not cast a primitive store
          s"{ val ks: scala.Any = this.keys$$field; val vs: scala.Any = this.values$$field; var i: scala.Int = 0; val n: scala.Int = this.size; " +
            s"while (i < n) { ${call(
                s"mkK.get(mkK.castArray(ks.asInstanceOf[scala.Array[?]]), i).asInstanceOf[$K]",
                s"mkV.get(mkV.castArray(vs.asInstanceOf[scala.Array[?]]), i).asInstanceOf[$V]"
              )}; i += 1 } }"
        s"lowlevel.MkArray.withResolved[$K, scala.Unit](${mkOf(K)}) { [BK, MkK <: lowlevel.MkArray[BK]] => (mkK: MkK) => " +
          s"lowlevel.MkArray.withResolved[$V, scala.Unit](${mkOf(V)}) { [BV, MkV <: lowlevel.MkArray[BV]] => (mkV: MkV) => $inner } }"
      else if !k.objectTable then
        // `entries()` is the one iterator every emitted hash family implements as a `JavaIterator`
        s"{ val it = this.entries(); while (it.hasNext()) { val e = it.next(); ${call("e.key", k.unwrap("e.value"))} } }"
      else
        // The arrays are read as `Array[AnyRef]` and each element narrowed: expanded where the key
        // type is known, a local typed `Array[K]` would cast the whole `Object[]` store to `K[]`.
        val refs = ".asInstanceOf[scala.Array[scala.AnyRef]]"
        // an ordered key's value is read from its table slot: the key is present, so `locateKey`
        // answers its index. Not `get(key)` — that wraps the value, and the only unwrapping that keeps
        // a stored null is one lls marks deprecated, which fails a caller compiling with fatal warnings
        // once this body is expanded there (sge: 1 error)
        val orderedValue = if useValue then s"vs(this.locateKey(key)).asInstanceOf[$V]" else "null"
        val ordered      =
          s"{ val ord = o.orderedKeys; val ks = ord.items$refs; val vs = this.valueTable$refs; val n: scala.Int = ord.size; var i: scala.Int = 0; " +
            s"while (i < n) { val key: $K = ks(i).asInstanceOf[$K]; ${call("key", orderedValue)}; i += 1 } }"
        val table =
          s"{ val ks = this.keyTable$refs; val vs = this.valueTable$refs; val n: scala.Int = ks.length; var i: scala.Int = 0; " +
            s"while (i < n) { val slot = ks(i); if (slot ne null) { val key: $K = slot.asInstanceOf[$K]; ${call("key", s"vs(i).asInstanceOf[$V]")} } else () ; i += 1 } }"
        s"this match { case o: lowlevel.util.OrderedMap[?, ?] => $ordered; case _ => $table }"
    val core = List(
      ("nonEmpty", 0, "def nonEmpty: scala.Boolean = this.size != 0"),
      // `inline` with an `inline` function, as in the hand-written lls: no function object per traversal
      ("foreachKey", 1, s"inline def foreachKey(inline f: $K => scala.Unit): scala.Unit = ${walk(false, (key, _) => s"f($key)")}"),
      ("foreachValue", 1, s"inline def foreachValue(inline f: $V => scala.Unit): scala.Unit = ${walk(true, (_, value) => s"f($value)")}"),
      ("foreachEntry", 1, s"inline def foreachEntry(inline f: ($K, $V) => scala.Unit): scala.Unit = ${walk(true, (key, value) => s"f($key, $value)")}"),
      ("update", 2, s"def update(key: $K, value: $V): scala.Unit = { this.put(key, ${k.wrap("value")}); () }"),
      ("$plus$eq", 1, s"@scala.annotation.targetName(\"plusEquals\") def +=(kv: ($K, $V)): scala.Unit = this.update(kv._1, kv._2)")
    )
    val optional =
      (if k.getOne.isEmpty then Nil
       else List(("apply", 1, s"def apply(key: $K): ${k.getOne} = this.get(key)"))) ++
        (if k.removeOne.isEmpty then Nil
         else
           List(
             ("$minus$eq", 1, s"@scala.annotation.targetName(\"minusEquals\") def -=(key: $K): scala.Unit = { this.${k.removeOne}(key); () }")
           ))
    val factories =
      if k.self.isEmpty then Nil
      else if k.capacityCtor then
        List(
          ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
          ("apply", 1, s"def apply${k.tparams}(capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(capacity)"),
          ("apply", 2, s"def apply${k.tparams}(ordered: scala.Boolean, capacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(ordered, capacity)")
        )
      else
        List(
          ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
          ("apply", 1, s"def apply${k.tparams}(initialCapacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(initialCapacity)"),
          ("apply", 2, s"def apply${k.tparams}(initialCapacity: scala.Int, loadFactor: scala.Float)${k.mk}: ${k.self} = new ${k.self}(initialCapacity, loadFactor)")
        )
    (core ++ optional).map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  // ---------------------------------------------------------------------------------------------
  // KIND 3 — set-like. `iterator()` is the only traversal the emitted surface offers, and
  // `OrderedSet` overrides it, so the inherited members follow the subclass's order.
  // ---------------------------------------------------------------------------------------------

  /** @param hasNext
    *   `hasNext()` on the object sets, a `hasNext` FIELD on `IntSet` (the emitter renames the field only where a method of the same name exists).
    */
  case class SetKind(
    owner:   String,
    elem:    String,
    self:    String,
    tparams: String = "",
    hasNext: String = "hasNext()",
    mk:      String = "",
    /** see [[MapKind.objectTable]]: `ObjectSet`'s own storage, `OrderedSet` its ordered subclass. */
    objectTable: Boolean = false
  )

  def setMembers(k: SetKind): List[(String, MemberSpec)] =
    val E   = k.elem
    val why = "lls collection API on the emitted set surface"
    // Over the backing storage where it is `ObjectSet`'s (see `mapMembers`): the key table in slot
    // order — the iterator's own order — and an `OrderedSet` in insertion order through
    // `orderedItems`; any other set family through its iterator. `go` is the loop's continuation
    // test, `body` sees the element as `key`.
    def scan(go: String, body: String): String =
      if !k.objectTable then s"{ val it = this.iterator(); while (it.${k.hasNext} && $go) { val key: $E = it.next(); $body } }"
      else objectScan(go, body)
    def objectScan(go: String, body: String): String =
      val refs    = ".asInstanceOf[scala.Array[scala.AnyRef]]"
      val ordered =
        s"{ val ord = o.orderedItems; val ks = ord.items$refs; val n: scala.Int = ord.size; var i: scala.Int = 0; " +
          s"while (i < n && $go) { val key: $E = ks(i).asInstanceOf[$E]; $body; i += 1 } }"
      val table =
        s"{ val ks = this.keyTable$refs; val n: scala.Int = ks.length; var i: scala.Int = 0; " +
          s"while (i < n && $go) { val slot = ks(i); if (slot ne null) { val key: $E = slot.asInstanceOf[$E]; $body } else () ; i += 1 } }"
      s"this match { case o: lowlevel.util.OrderedSet[?] => $ordered; case _ => $table }"
    val core = List(
      ("nonEmpty", 0, "def nonEmpty: scala.Boolean = this.size != 0"),
      ("foreach", 1, s"inline def foreach(inline f: $E => scala.Unit): scala.Unit = ${scan("true", "f(key)")}"),
      ("exists", 1, s"inline def exists(inline p: $E => scala.Boolean): scala.Boolean = { var r: scala.Boolean = false; ${scan("!r", "if (p(key)) { r = true } else ()")}; r }"),
      ("forall", 1, s"inline def forall(inline p: $E => scala.Boolean): scala.Boolean = { var r: scala.Boolean = true; ${scan("r", "if (!p(key)) { r = false } else ()")}; r }"),
      ("count", 1, s"inline def count(inline p: $E => scala.Boolean): scala.Int = { var c: scala.Int = 0; ${scan("true", "if (p(key)) { c += 1 } else ()")}; c }"),
      ("$plus$eq", 1, s"@scala.annotation.targetName(\"plusEquals\") def +=(key: $E): scala.Unit = { this.add(key); () }"),
      ("$minus$eq", 1, s"@scala.annotation.targetName(\"minusEquals\") def -=(key: $E): scala.Unit = { this.remove(key); () }")
    )
    val factories = List(
      ("apply", 0, s"def apply${k.tparams}()${k.mk}: ${k.self} = new ${k.self}()"),
      ("apply", 1, s"def apply${k.tparams}(initialCapacity: scala.Int)${k.mk}: ${k.self} = new ${k.self}(initialCapacity)"),
      ("apply", 2, s"def apply${k.tparams}(initialCapacity: scala.Int, loadFactor: scala.Float)${k.mk}: ${k.self} = new ${k.self}(initialCapacity, loadFactor)")
    )
    core.map((n, a, s) => spec(k.owner, n, a, s, why)) ++
      factories.map((n, a, s) => spec(k.owner, n, a, s, why, static = true))

  // ---------------------------------------------------------------------------------------------
  // The population. Only the ROOT of each override component is enriched: `OrderedMap`,
  // `OrderedMap` and `OrderedSet` INHERIT the members
  // (adding them there would owe an `override` the mechanism cannot spell).
  // ---------------------------------------------------------------------------------------------

  private def arrays(w: Boolean): List[ArrayKind] = List(
    // `Array` takes the type class: with the `witness` step on its element type loses java's implicit
    // `<: java.lang.Object` bound and its constructors take the type class, so every factory
    // written here must lose the bound and supply the clause too.
    ArrayKind(
      "Array",
      "lowlevel.util.DynamicArray[T]",
      "T",
      if w then "[T]" else "[T <: java.lang.Object]",
      removeOne = "this.removeValue(lowlevel.Nullable(value), false)",
      removeMany = "this.removeAll(other, false)",
      mk = if w then "(using lowlevel.MkArray[T])" else ""
    )
  )

  private def maps(w: Boolean): List[MapKind] = List(
    MapKind(
      "ObjectMap",
      "K",
      "V",
      "lowlevel.Nullable[V]",
      tparams = "[K <: java.lang.Object, V <: java.lang.Object]",
      self = "lowlevel.util.ObjectMap[K, V]",
      wrap = v => s"lowlevel.Nullable($v)",
      unwrap = v => s"$v.orNull",
      getOne = "lowlevel.Nullable[V]",
      removeOne = "remove",
      objectTable = true
    ),
    MapKind(
      "ArrayMap",
      "K",
      "V",
      "V",
      tparams = if w then "[K, V]" else "[K <: java.lang.Object, V <: java.lang.Object]",
      self = "lowlevel.util.ArrayMap[K, V]",
      getOne = "lowlevel.Nullable[V]",
      removeOne = "removeKey",
      indexed = true,
      capacityCtor = true,
      mk = if w then "(using lowlevel.MkArray[K], lowlevel.MkArray[V])" else ""
    )
  )

  private val sets: List[SetKind] = List(
    SetKind("ObjectSet", "T", "lowlevel.util.ObjectSet[T]", "[T <: java.lang.Object]", objectTable = true)
  )

  /** The two SUBCLASSES get the factories too — and must. A companion factory is a static, so the `export Parent.*` that reproduces java's static inheritance (`JS-C3`) delivers the parent's into the
    * subclass, where scala's own CONSTRUCTOR PROXY `apply` is a second definition with matching parameter types (`E120`). Declaring them here excludes the parent's and suppresses the proxy, and the
    * factory answers with the SUBCLASS's type, which is what a caller wants.
    */
  private def subclassFactories(): List[(String, MemberSpec)] =
    val why                                                     = "lls factory on a subclass; also what keeps the inherited-statics export unambiguous"
    def tableLike(owner: String, self: String, tparams: String) = List(
      ("apply", 0, s"def apply$tparams(): $self = new $self()"),
      ("apply", 1, s"def apply$tparams(initialCapacity: scala.Int): $self = new $self(initialCapacity)"),
      ("apply", 2, s"def apply$tparams(initialCapacity: scala.Int, loadFactor: scala.Float): $self = new $self(initialCapacity, loadFactor)")
    ).map((n, a, s) => spec(owner, n, a, s, why, static = true))
    tableLike("OrderedMap", "lowlevel.util.OrderedMap[K, V]", "[K <: java.lang.Object, V <: java.lang.Object]") ++
      tableLike("OrderedSet", "lowlevel.util.OrderedSet[T]", "[T <: java.lang.Object]")

  /** ArrayMap's own flag-free overloads — the same shape as `Array`'s, on the two members that carry java's `identity` argument here.
    */
  private val arrayMapExtras: List[(String, MemberSpec)] = List(
    spec(
      "ArrayMap",
      "containsValue",
      1,
      "def containsValue(value: V): scala.Boolean = this.containsValue(value, false)",
      "lls's flag-free spelling of java's identity argument"
    ),
    spec(
      "ArrayMap",
      "indexOfValue",
      1,
      "def indexOfValue(value: V): scala.Int = this.indexOfValue(value, false)",
      "lls's flag-free spelling of java's identity argument"
    )
  )

  private def all(w: Boolean): List[(String, MemberSpec)] =
    arrays(w).flatMap(arrayMembers) ++ refArrayExtras(w) ++
      maps(w).flatMap(mapMembers) ++ arrayMapExtras ++ sets.flatMap(setMembers) ++
      subclassFactories()

  /** owner class name -> specs, the shape `AddMembersTransform` takes — for any port's table built from the templates above. */
  def build(pairs: List[(String, MemberSpec)]): Map[String, List[MemberSpec]] =
    pairs.groupBy(_._1).map((owner, ms) => owner -> ms.map(_._2)).toMap

  /** lls's own table. @param w the `witness` step is on. */
  def members(w: Boolean): Map[String, List[MemberSpec]] = build(all(w))

  /** how many members lls adds. */
  def count: Int = all(false).size

  def transform(w: Boolean = false): AddMembersTransform = new AddMembersTransform(members(w))
