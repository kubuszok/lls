# Low Level Scala (lls) — working rules

Cross-platform (JVM, Scala.js, Scala Native) low-level utilities for Scala 3: unboxed collections,
array views, `Nullable`, `Eval`/`Resource`. sge and ssg depend on it, so its public API is a contract.

## Layout

| path | what |
|---|---|
| `lls/src/main/scala` | hand-written code (`ArrayView`, `MkArray`, `Nullable`, `Eval`, glue) |
| `target/balticporter-lls/src_managed/main/scala` | GENERATED collections — never edited, never committed |
| `lls/src/test/scala`, `lls/src/test/scalajvm` | tests (all platforms / JVM-only, e.g. the bytecode checks) |
| `lls-port` | how the generated collections are ported (the Baltic Porter policy); JVM only, published |
| `lls-io` | I/O utilities |
| `lls-bench` | JMH suites and the recorded `baseline.txt` |
| `original-src/libgdx` | upstream Java, a submodule — the generator's input |
| `.rescale/data/issues.tsv` | open work, tracked with `re-scale db issues <list|add|resolve>` |

## Generated code (Baltic Porter)

The collections are ported mechanically from libGDX's Java by
[Baltic Porter](https://github.com/kubuszok/balticporter): a `sourceGenerator` in `build.sbt` calls
`project/BalticPorterGen.scala`, which runs the engine inside sbt and writes to
`target/balticporter-lls/`. The engine (`balticporter-engine`) is the snapshot pinned in
`project/plugins.sbt`, resolved from Maven Central's snapshot repository; no engine checkout is needed.

**How lls is ported is decided here, in `lls-port/src/main/scala/lowlevel/port/`** (which files,
drops, renames, added members, replacement bodies). That one directory is compiled twice: into the
meta-build (`project/build.sbt`), where the generator calls it, and as the published JVM module
`lls-port`, which sge pins beside the lls version to extend the same policy.

- **Never edit a generated file, and never patch a hand-written file to fit a generated defect.**
  A wrong decision is fixed in `lls-port/`; a wrong mechanism is fixed in Baltic Porter, then the
  pin is bumped here.
- After editing `lls-port/`, `reload` (the generator refuses to run a policy older than the one on
  disk); the next compile regenerates.
- The generated tree is reused while `target/balticporter-lls/.generated-marker` matches the engine
  pin, the libGDX commit, the generator source, the `lls-port` sources and the JDK major. Force with
  `-Dbalticporter.forceRegen=true`; `sbt --client generatePort` runs the generation alone.
- Bump the pin only to a hash whose `.pom` is already on the snapshot repository.
- The procedures (tracing a generated defect to its rule, bumping the pin, sbt 2, CI caching) are
  skills of the `balticporter` Claude Code plugin enabled in `.claude/settings.json`.

## Build

- JDK 25; the generated code depends on the JDK major.
- sbt only as `sbt --client "<tasks joined with ;>"`, one quoted string. Never bare `sbt`.
- Before any push: commit, then `sbt --client verifyLocal` (JVM, Scala.js and Scala Native tests;
  records the verified commit in `target/local-verification`). The plugin's hook refuses a push
  without it.
- Aliases live in `build.sbt`, never in workflow YAML. CI generates once (`generate` job) and every
  other job restores the generated tree.

## API rules

- Java's behaviour is the default contract. A difference from the Java original is acceptable only
  when it adds `Nullable`, makes something `final`, or adds a test; anything else is recorded with
  its justification or fixed (ISS-001).
- `Nullable[A]` stays — never replace it with `A | Null`.
- A performance sentence in the README is backed by a test (`OverheadClaimsSuite` reads the
  compiled bytecode); change the claim and the test together.
- Signed commits, linear history (rebase, never merge), no attribution lines.
