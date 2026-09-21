package lowlevel.port

import balticporter.core.PortManifest
import balticporter.transform.{ CollectionsTransform, ElementWitnessTransform, GlobalsToImplicitsTransform, MutableParamsTransform, NullabilityTransform, TypeRedirectTransform }

/** The libGDX sources lls ports: `utils.{Array, ObjectMap, ObjectSet, OrderedMap, OrderedSet, ArrayMap, Sort, TimSort, ComparableTimSort, Select, QuickSelect}` and `math.MathUtils`, onto
  * `lowlevel.{util,math}`. Every other libGDX type they mention is external to this port.
  */
object LlsMigrate:

  /** The twelve java files, relative to `gdx/src`, plus the `@Null` annotation they carry (a site that keeps the annotation needs the type). A port of the rest of libGDX excludes exactly these.
    */
  val Files: List[String] = List(
    "com/badlogic/gdx/math/MathUtils.java",
    "com/badlogic/gdx/utils/Null.java",
    "com/badlogic/gdx/utils/Array.java",
    "com/badlogic/gdx/utils/ArrayMap.java",
    "com/badlogic/gdx/utils/ComparableTimSort.java",
    "com/badlogic/gdx/utils/ObjectMap.java",
    "com/badlogic/gdx/utils/ObjectSet.java",
    "com/badlogic/gdx/utils/OrderedMap.java",
    "com/badlogic/gdx/utils/OrderedSet.java",
    "com/badlogic/gdx/utils/QuickSelect.java",
    "com/badlogic/gdx/utils/Select.java",
    "com/badlogic/gdx/utils/Sort.java",
    "com/badlogic/gdx/utils/TimSort.java"
  )

  /** The twelve as upstream class names (the annotation has no members to scope): what this port governs, and the scope of every step in its policy. */
  val Fqns: Set[String] = Files.map(_.stripSuffix(".java").replace('/', '.')).toSet - "com.badlogic.gdx.utils.Null"

/** How lls is ported, as a value: the manifest a port of the rest of libGDX extends. */
object LlsPolicy:

  /** lls's array type class. */
  val Witness = "lowlevel.MkArray"

  /** The array-like declarations that allocate element arrays through the type class: upstream class name -> the element type-parameter indexes. The open-addressed tables are absent on purpose —
    * `keyTable[i] == null` is their occupancy test.
    */
  val WitnessSubjects: Map[String, List[Int]] = Map(
    "com.badlogic.gdx.utils.Array" -> List(0),
    "com.badlogic.gdx.utils.ArrayMap" -> List(0, 1),
    // the two nested views CONSTRUCT a `DynamicArray` at their own parameter, so they take the
    // clause even though they allocate nothing themselves.
    "com.badlogic.gdx.utils.ArrayMap$Values" -> List(0),
    "com.badlogic.gdx.utils.ArrayMap$Keys" -> List(0)
  )

  /** …and the declarations that only lose java's implicit `Object` bound: the sort/select entry points the array family calls with its own element type. `TimSort` is not among them — `Sort` holds it
    * in a raw field and hands it `Object[]`, so an unbounded element type there would type-check and throw.
    */
  val WitnessUnbound: Set[String] = WitnessSubjects.keySet ++ Set(
    "com.badlogic.gdx.utils.Sort",
    "com.badlogic.gdx.utils.Select",
    "com.badlogic.gdx.utils.QuickSelect"
  )

  /** libGDX's array factory is not lls's: java's `T[] get(int)` becomes `scala.Function1[Int, T[]]` and the default factory is the type class (`MkArray.create`), so no factory type is emitted.
    */
  val ArraySupplier = "com.badlogic.gdx.utils.ArraySupplier"
  def collections(rungs: Set[String]): CollectionsTransform = new CollectionsTransform(
    scope = Twelve,
    retarget = Map(ArraySupplier -> "scala.Function1") ++
      (if rungs("ordering") then Map("java.util.Comparator" -> "scala.math.Ordering") else Map.empty),
    retargetTypeArgs = Map(
      ArraySupplier -> List(CollectionsTransform.RetargetArg.FixedType("scala.Int"), CollectionsTransform.RetargetArg.SourceArg(0))
    ),
    retargetRewrites = Map(ArraySupplier -> Map(("get", 1) -> CollectionsTransform.RetargetRewrite.Rename("apply")))
  )

  /** The optional steps of the policy, each a list of phases, by name.
    * @param rungs
    *   the steps switched on — `enrich`'s verbatim members are written against the signatures `witness` decides, so the two are not independent.
    */
  def rungPhases(rungs: Set[String]): Map[String, List[balticporter.tir.Phase]] = Map(
    "nullable" -> List(
      new NullabilityTransform(
        annotations = Set("com.badlogic.gdx.utils.Null"),
        target = NullabilityTransform.Target.Named("lowlevel.Nullable"),
        scope = balticporter.tir.RuleScope.Only(Annotated)
      )
    ),
    "ordering" -> List(collections(rungs)),
    "renames" -> List(
      new balticporter.transform.MemberRenameTransform(
        renames = Map(
          "com.badlogic.gdx.utils.Array#first" -> "head",
          "com.badlogic.gdx.utils.ObjectSet#first" -> "head",
          "com.badlogic.gdx.utils.OrderedSet#first" -> "head"
        )
      )
    ),
    // getter-like methods that lose their `()`
    "arity" -> List(
      new balticporter.transform.NullaryArityTransform(
        scope = Twelve,
        force = Set(
          "com.badlogic.gdx.utils.Array#head",
          "com.badlogic.gdx.utils.ArrayMap#firstKey",
          "com.badlogic.gdx.utils.ArrayMap#firstValue",
          "com.badlogic.gdx.utils.Array#isEmpty",
          "com.badlogic.gdx.utils.ObjectMap#isEmpty",
          "com.badlogic.gdx.utils.OrderedSet#first",
          "com.badlogic.gdx.utils.OrderedSet#orderedItems"
        )
      )
    ),
    // java-convention accessor pairs become properties (empty tables: derivation only)
    "bean" -> List(new balticporter.transform.BeanPropertyTransform(Map.empty, Map.empty, scope = Twelve)),
    "enrich" -> List(LlsEnrich.transform(rungs("witness"))),
    "witness" -> List(
      // the constructor half of the clause, threaded by the phase that owns that mechanism
      new GlobalsToImplicitsTransform(requiredGivens = ElementWitnessTransform.constructorGivens(WitnessSubjects, Witness)),
      new ElementWitnessTransform(
        witness = Witness,
        subjectTypes = WitnessSubjects,
        dropBound = WitnessUnbound,
        // java's own default array factory: with this step on, the type class IS it.
        defaultSuppliers = Map(
          "com.badlogic.gdx.utils.ArraySupplier#object()" ->
            "((size: scala.Int) => scala.Predef.summon[lowlevel.MkArray[{elem}]].create(size))"
        ),
        // the type class for an element type that KEEPS java's `Object` bound: the one lls itself
        // uses for reference elements, which is the representation java's `Object[]` already had.
        boxedWitness = Some("lowlevel.MkArray.anyRef[scala.AnyRef].asInstanceOf[lowlevel.MkArray[{elem}]]")
      ),
      // the answer to every array the step above counts as presented to java's code as `Object[]`;
      // its own group, so a dependent's body replacements keep the positions that dependent chose
      new balticporter.transform.MethodBodyTransform(LlsPrimitiveArrays.bodies, group = "primitive-arrays")
    )
  )

  /** the step names, for validation. */
  val Rungs: Set[String] = rungPhases(Set.empty).keySet

  /** the order the steps take in the pipeline — a position, not the alphabet. */
  val RungOrder: List[String] = List("renames", "bean", "arity", "nullable", "ordering", "enrich", "witness")

  /** the steps lls is built with. */
  val DefaultRungs: Set[String] = Set("renames", "arity", "nullable", "ordering", "enrich", "witness")

  /** every step stops at lls's own declarations: a port that extends this policy decides its own types itself. */
  val Twelve: balticporter.tir.RuleScope = balticporter.tir.RuleScope.Only(LlsMigrate.Fqns)

  /** the four of the twelve that carry `@Null` plus the two whose overrides they reach — a scope cut through a family of overrides splits it, so neither `Twelve` nor the four alone will do.
    */
  val Annotated: Set[String] = Set("Array", "ArrayMap", "ObjectMap", "ObjectSet", "OrderedMap", "OrderedSet").map("com.badlogic.gdx.utils." + _)

  /** `GdxRuntimeException` and `RandomXS128` are not lls's: inside the twelve they are the JDK types lls used, scoped so a port extending this policy keeps its own uses. `Collections` is lls's
    * hand-written flag holder everywhere, because the drop is inherited and so is its replacement.
    */
  val redirects: TypeRedirectTransform = new TypeRedirectTransform(
    redirects = Map(
      "com.badlogic.gdx.utils.GdxRuntimeException" -> "java.lang.RuntimeException",
      "com.badlogic.gdx.utils.Collections" -> "lowlevel.util.Collections",
      "com.badlogic.gdx.math.RandomXS128" -> "java.util.Random"
    ),
    scopes = Map(
      "com.badlogic.gdx.utils.GdxRuntimeException" -> Twelve,
      "com.badlogic.gdx.utils.Collections" -> balticporter.tir.RuleScope.Everywhere(Set.empty),
      "com.badlogic.gdx.math.RandomXS128" -> balticporter.tir.RuleScope.Only(Set("com.badlogic.gdx.math.MathUtils"))
    )
  )

  /** The manifest. It injects nothing (`lowlevel.util.Collections` is hand-written in lls) and names no hand port to compare against.
    * @param rungs
    *   the optional steps to switch on; `DefaultRungs` is what lls ships.
    */
  def core(rungs: Set[String] = Set.empty): PortManifest =
    val unknown = rungs -- Rungs
    require(unknown.isEmpty, s"unknown lls rungs: ${unknown.mkString(",")}; known: ${Rungs.toList.sorted.mkString(",")}")
    require(RungOrder.toSet == Rungs, s"RungOrder does not cover every rung: ${Rungs -- RungOrder.toSet}")
    // `enrich`'s bodies are written against the EMITTED signatures, which `nullable` decides
    // (`contains(Nullable[T], Boolean)` vs `contains(T, Boolean)`).
    require(!rungs("enrich") || rungs("nullable"), "lls rung `enrich` requires `nullable`")
    PortManifest(
      name = "lls",
      governs = LlsMigrate.Fqns,
      // the references the twelve make outside themselves, answered the way lls did: `Collections`
      // is dropped and redirected to the hand-written one; the reflective `Class`-typed constructors
      // and `toArray(Class)` go with `ArrayReflection`; `select(Predicate)` goes with `Predicate`;
      // `ArraySupplier` retargets.
      dropTypes = Set("com.badlogic.gdx.utils.Collections"),
      dropMethods = Set(
        "com.badlogic.gdx.utils.Array#<init>(boolean,int,Class)",
        "com.badlogic.gdx.utils.Array#<init>(Class)",
        "com.badlogic.gdx.utils.Array#toArray(Class)",
        "com.badlogic.gdx.utils.Array#of(Class)",
        "com.badlogic.gdx.utils.Array#of(boolean,int,Class)",
        "com.badlogic.gdx.utils.Array#select(Predicate)",
        "com.badlogic.gdx.utils.Array#predicateIterable",
        "com.badlogic.gdx.utils.ArrayMap#<init>(boolean,int,Class,Class)",
        "com.badlogic.gdx.utils.ArrayMap#<init>(Class,Class)"
      ),
      // the types and their package-private members other ports read from the same java package
      // (`ObjectSet.tableSize`, `ObjectMap.dummy`, `TimSort` from java's own `SortTest`): the move
      // out of the package is declared, so they ship public.
      allowPackageSplit = Set(
        "com.badlogic.gdx.utils.Array",
        "com.badlogic.gdx.utils.ObjectMap",
        "com.badlogic.gdx.utils.ObjectSet",
        "com.badlogic.gdx.utils.TimSort",
        "com.badlogic.gdx.utils.ComparableTimSort"
      ),
      // a move per type, never a package claim — the rest of `utils`/`math` belongs to other ports.
      // `Array` is `DynamicArray` (`scala.Array` is taken).
      typeRenames = LlsMigrate.Files.map { f =>
        val fqn    = f.stripSuffix(".java").replace('/', '.')
        val simple = if fqn.endsWith(".Array") then "DynamicArray" else fqn.substring(fqn.lastIndexOf('.') + 1)
        val pkg    = if fqn.startsWith("com.badlogic.gdx.math.") then "lowlevel.math" else "lowlevel.util"
        fqn -> s"$pkg.$simple"
      }.toMap,
      // `enrich` LAST among the steps: its members are verbatim text written against what the
      // steps before it emit.
      surface = List(new MutableParamsTransform, redirects) ++
        RungOrder.filter(rungs).flatMap(rungPhases(rungs)(_)) ++
        (if rungs("ordering") then Nil else List(collections(rungs)))
    )
