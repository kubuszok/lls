import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }

/** sbt sourceGenerator that uses Baltic Porter to mechanically port the twelve libGDX utility sources lls carries into Scala 3.
  *
  * Requires:
  *   - libGDX sources at `original-src/libgdx/gdx/src` (git submodule)
  *   - balticporter checkout at `../balticporter` (sibling directory) for inject files and classpath cache; override with `-Dbalticporter.root=<path>`
  *   - `balticporter-corpus` 0.1.0-SNAPSHOT published locally (`sbt publishLocal` in balticporter)
  */
object BalticPorterGen {

  /** Generate lls Scala sources from libGDX Java originals. Caches by upstream commit. */
  def generate(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val llsRoot = buildBase.toPath.toAbsolutePath.normalize
    val bpRoot  = Path.of(sys.props.getOrElse("balticporter.root", llsRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

    val libgdxSrc = llsRoot.resolve("original-src/libgdx/gdx/src")
    if (!Files.isDirectory(libgdxSrc) || !Files.isDirectory(bpRoot.resolve("balticporter/corpus"))) {
      log.warn("[Baltic Porter] No libGDX submodule or balticporter sibling — skipping lls generation")
      val outDir = llsRoot.resolve("target/balticporter-lls/src_managed/main/scala")
      return if (Files.isDirectory(outDir)) collectScalaFiles(outDir) else Nil
    }

    val portRoot = llsRoot.resolve("target/balticporter-lls")
    val outDir   = portRoot.resolve("src_managed/main/scala")
    val marker   = portRoot.resolve(".generated-marker")

    // Cache key: the vendored tree's commit
    val commit = balticporter.runner.VendoredCommit.of(libgdxSrc)
    val cached = Files.exists(marker) &&
      Files.exists(outDir) &&
      Files.readString(marker).trim == commit

    if (!cached) {
      log.info(s"[Baltic Porter] Generating lls sources from libGDX ($commit)")

      val rungs = balticporter.corpus.lls.LlsPolicy.DefaultRungs
      // Disable parity check: the hand-ported files are being replaced by these generated ones.
      // Remove inject: Collections.scala lives in lls's hand-written source tree (brace syntax
      // for -no-indent); the porter's inject would duplicate it with indentation syntax.
      val manifest = balticporter.corpus.lls.LlsPolicy.core(bpRoot, rungs).copy(parity = None, inject = Nil)

      val result = balticporter.runner
        .PortRun(
          label = "lls",
          portRoot = portRoot,
          sourceSet = balticporter.runner.SourceSet.Main,
          frontend = balticporter.core.FrontendConfig(
            libgdxSrc,
            balticporter.corpus.lls.LlsMigrate.Files,
            balticporter.corpus.GdxCoreClasspath.entries(bpRoot),
            resolutionRoots = Nil
          ),
          phases = Nil,
          manifest = Some(manifest),
          provenance = Some(
            balticporter.core.Provenance(
              upstreamName = "libGDX",
              upstreamCommit = commit,
              originalLicense = "Apache-2.0",
              sourcePathPrefix = "gdx/src",
              sourceRoot = libgdxSrc.toString
            )
          ),
          runtimeMode = balticporter.core.RuntimeMode.Vendored,
          determinism = balticporter.runner.Determinism.Emission,
          nextStep = ""
        )
        .execute()

      // Scala.js workaround: the engine emits named boundary/break or local-def+return for
      // Java's labeled `break outer;` across nested loops. Scala.js incorrectly lowers these
      // to a JS `break` that exits only the innermost loop. Replace with throw/catch using a
      // ControlThrowable sentinel that exception propagation handles correctly on all backends.
      patchNamedBreaks(outDir, log)

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
      log.info(s"[Baltic Porter] Generated ${result.written} files to $outDir")
    } else {
      log.info(s"[Baltic Porter] Using cached generated sources ($commit)")
    }

    collectScalaFiles(outDir)
  }

  /** Patch generated Scala files that contain named boundary/break patterns. Uses literal string replacement on the known generated patterns -- not regex -- to avoid corrupting unrelated text.
    *
    * Two forms the engine may produce: (A) `scala.util.boundary { (brk$N: scala.util.boundary.Label[scala.Unit]) ?=> LOOP }` with `scala.util.boundary.break(())(using brk$N)` for breaks (B)
    * `{ def brk$N(): scala.Unit = { LOOP }; brk$N() }` with `return` for breaks
    *
    * Both are replaced with throw/catch: `{ val brk$N = new scala.util.control.ControlThrowable {}; try { LOOP } catch { ... } }` with `throw brk$N` for breaks
    */
  private def patchNamedBreaks(outDir: Path, log: sbt.util.Logger): Unit = {
    val stream = Files.walk(outDir)
    try
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) {
          val content = Files.readString(p)
          // Only patch files that have the boundary-with-named-label pattern or the def-brk pattern
          val hasPatternA = content.contains("scala.util.boundary.break(())(using brk$")
          val hasPatternB = content.contains("{ def brk$") && content.contains("(); brk$")
          if (hasPatternA || hasPatternB) {
            val patched = patchContent(content, hasPatternA, hasPatternB)
            if (patched != content) {
              Files.writeString(p, patched)
              log.info("[Baltic Porter] Patched " + p.getFileName + " for Scala.js labeled-break compatibility")
            }
          }
        }
      }
    finally stream.close()
  }

  private def patchContent(content: String, hasA: Boolean, hasB: Boolean): String = {
    var text = content

    if (hasA) {
      // Find all named break labels used in pattern A
      val namePattern = java.util.regex.Pattern.compile("scala\\.util\\.boundary\\.break\\(\\(\\)\\)\\(using (brk\\$\\d+)\\)")
      val matcher     = namePattern.matcher(text)
      val names       = scala.collection.mutable.Set[String]()
      while (matcher.find()) names += matcher.group(1)

      for (name <- names) {
        // Replace the boundary opening
        text = text.replace(
          s"scala.util.boundary { ($name: scala.util.boundary.Label[scala.Unit]) ?=>",
          s"{ val $name = new scala.util.control.ControlThrowable {}; try {"
        )
        // Replace break calls
        text = text.replace(
          s"scala.util.boundary.break(())(using $name)",
          s"throw $name"
        )
        // Replace boundary closing: the boundary block ends with `} }` after the while loop.
        // After our replacement, we have `try { while(true) { ... } }` and need to add catch.
        // Find the `try {` we inserted and brace-match to its closing `}`.
        text = addCatchClause(text, name)
      }
    }

    if (hasB) {
      val namePattern = java.util.regex.Pattern.compile("\\{ def (brk\\$\\d+)\\(\\): scala\\.Unit = \\{")
      val matcher     = namePattern.matcher(text)
      val names       = scala.collection.mutable.Set[String]()
      while (matcher.find()) names += matcher.group(1)

      for (name <- names) {
        // Replace def opening
        text = text.replace(
          s"{ def $name(): scala.Unit = {",
          s"{ val $name = new scala.util.control.ControlThrowable {}; try {"
        )
        // Replace def closing
        text = text.replace(
          s"}; $name() }",
          s"} catch { case $$e: scala.util.control.ControlThrowable if $$e eq $name => () } }"
        )
        // Replace return with throw (only standalone returns, not return-values)
        text = text.replaceAll("(?m)^(\\s+)return$", s"$$1throw $name")
      }
    }

    text
  }

  /** Find the `try {` block for the given sentinel and add a catch clause at its closing `}`. */
  private def addCatchClause(text: String, name: String): String = {
    val tryMarker = s"val $name = new scala.util.control.ControlThrowable {}; try {"
    val idx       = text.indexOf(tryMarker)
    if (idx < 0) return text

    // Start scanning after `try {`
    val tryOpenIdx = text.indexOf("try {", idx)
    if (tryOpenIdx < 0) return text
    val scanStart = tryOpenIdx + 5

    // Brace-match to find the closing `}` of the try body
    var depth = 1
    var j     = scanStart
    // Skip string literals and comments to avoid matching braces inside them
    while (j < text.length && depth > 0) {
      val ch = text.charAt(j)
      if (ch == '{') depth += 1
      else if (ch == '}') depth -= 1
      j += 1
    }
    // j is now past the `}` closing the try body
    if (depth != 0) return text // unbalanced braces, don't patch

    // Insert catch clause. The replacement added an extra `{` (for `{ val brk$N = ...`)
    // where the original boundary had only one. So we need `catch { ... } }` to close both
    // the try and the outer val block.
    val catchClause = s" catch { case $$e: scala.util.control.ControlThrowable if $$e eq $name => () } }"
    text.substring(0, j) + catchClause + text.substring(j)
  }

  private def collectScalaFiles(dir: Path): Seq[File] = {
    if (!Files.isDirectory(dir)) return Seq.empty
    val stream = Files.walk(dir)
    try {
      val builder = Seq.newBuilder[File]
      stream.forEach { p =>
        if (p.toString.endsWith(".scala")) builder += p.toFile
      }
      builder.result()
    } finally
      stream.close()
  }
}
