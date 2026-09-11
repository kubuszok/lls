import sbt.*
import sbt.Keys.*

import java.nio.file.{Files, Path}

/** sbt sourceGenerator that uses Baltic Porter to mechanically port the twelve libGDX
  * utility sources lls carries into Scala 3.
  *
  * Requires:
  *   - libGDX sources at `original-src/libgdx/gdx/src` (git submodule)
  *   - balticporter checkout at `../balticporter` (sibling directory) for inject files
  *     and classpath cache; override with `-Dbalticporter.root=<path>`
  *   - `balticporter-corpus` 0.1.0-SNAPSHOT published locally (`sbt publishLocal` in balticporter)
  */
object BalticPorterGen {

  /** Generate lls Scala sources from libGDX Java originals. Caches by upstream commit. */
  def generate(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val llsRoot = buildBase.toPath.toAbsolutePath.normalize
    val bpRoot = Path.of(sys.props.getOrElse("balticporter.root",
      llsRoot.resolve("../balticporter").toString)).toAbsolutePath.normalize

    val libgdxSrc = llsRoot.resolve("original-src/libgdx/gdx/src")
    require(Files.isDirectory(libgdxSrc),
      s"libGDX sources not found at $libgdxSrc — run: git submodule update --init")

    val portRoot = llsRoot.resolve("target/balticporter-lls")
    val outDir = portRoot.resolve("src_managed/main/scala")
    val marker = portRoot.resolve(".generated-marker")

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
      val manifest = balticporter.corpus.lls.LlsPolicy.core(bpRoot, rungs)
        .copy(parity = None, inject = Nil)

      val result = balticporter.runner.PortRun(
        label     = "lls",
        portRoot  = portRoot,
        sourceSet = balticporter.runner.SourceSet.Main,
        frontend  = balticporter.core.FrontendConfig(
          libgdxSrc,
          balticporter.corpus.lls.LlsMigrate.Files,
          balticporter.corpus.GdxCoreClasspath.entries(bpRoot),
          resolutionRoots = Nil,
        ),
        phases    = Nil,
        manifest  = Some(manifest),
        provenance = Some(balticporter.core.Provenance(
          upstreamName     = "libGDX",
          upstreamCommit   = commit,
          originalLicense  = "Apache-2.0",
          sourcePathPrefix = "gdx/src",
          sourceRoot       = libgdxSrc.toString,
        )),
        runtimeMode = balticporter.core.RuntimeMode.Vendored,
        determinism = balticporter.runner.Determinism.Emission,
        nextStep    = "",
      ).execute()

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, commit)
      log.info(s"[Baltic Porter] Generated ${result.written} files to $outDir")
    } else {
      log.info(s"[Baltic Porter] Using cached generated sources ($commit)")
    }

    collectScalaFiles(outDir)
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
    } finally {
      stream.close()
    }
  }
}
