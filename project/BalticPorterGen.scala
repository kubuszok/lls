import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }

/** sbt sourceGenerator that uses Baltic Porter to mechanically port the twelve libGDX utility sources lls carries into Scala 3.
  *
  * Requires:
  *   - libGDX sources at `original-src/libgdx/gdx/src` (git submodule)
  *   - the `balticporter-corpus` artifact pinned in `project/plugins.sbt` (it carries the files the policy injects; no engine checkout is needed)
  *   - `cs` (coursier) on the PATH, to resolve the classpath libGDX's own sources are read against
  */
object BalticPorterGen {

  // sbt evaluates the JVM/JS/Native rows' managedSources in parallel; they share one output tree, so
  // the rows serialise here and the later ones read the marker the first one wrote.
  def generate(buildBase: File, log: sbt.util.Logger): Seq[File] =
    BalticPorterGen.synchronized(generateUnlocked(buildBase, log))

  private def generateUnlocked(buildBase: File, log: sbt.util.Logger): Seq[File] = {
    val llsRoot   = buildBase.toPath.toAbsolutePath.normalize
    val libgdxSrc = llsRoot.resolve("original-src/libgdx/gdx/src")
    val portRoot  = llsRoot.resolve("target/balticporter-lls")
    val outDir    = portRoot.resolve("src_managed/main/scala")
    val marker    = portRoot.resolve(".generated-marker")

    // Cache key: everything the generated tree depends on (see `fingerprint`), readable without the
    // submodule's files — so a checkout that RECEIVED the generated tree (a CI job restoring the
    // `generate` job's output) reuses it and needs neither the submodule nor a generation run.
    // Force a regeneration with -Dbalticporter.forceRegen=true, or delete the marker.
    val forceRegen = sys.props.getOrElse("balticporter.forceRegen", "false").toBoolean
    val expected   = fingerprint(llsRoot)
    val cached     = !forceRegen && Files.exists(marker) && Files.exists(outDir) && Files.readString(marker).trim == expected

    if (!cached && !Files.isDirectory(libgdxSrc))
      sys.error(
        "[Baltic Porter] The generated lls sources are missing or stale (" + marker + " does not read `" + expected + "`) and the libGDX submodule is not " +
          "initialised. Run `git submodule update --init --depth=1 original-src/libgdx`, or place a generated tree with a matching marker under " + portRoot + "."
      )

    if (!cached) {
      val commit = balticporter.runner.VendoredCommit.of(libgdxSrc)
      log.info(s"[Baltic Porter] Generating lls sources from libGDX ($commit)")

      // The files the port's policy injects by path ship inside the published corpus jar and are
      // unpacked under target/; -Dbalticporter.root=<engine checkout> reads them from a checkout
      // instead (engine development only).
      val bpRoot = sys.props.get("balticporter.root") match {
        case Some(root) => Path.of(root).toAbsolutePath.normalize
        case None       => balticporter.corpus.BundledCorpus.root(llsRoot.resolve("target/balticporter-engine"))
      }

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

      Files.createDirectories(marker.getParent)
      Files.writeString(marker, expected)
      log.info(s"[Baltic Porter] Generated ${result.written} files to $outDir")
    } else {
      log.info(s"[Baltic Porter] Using cached generated sources ($expected)")
    }

    collectScalaFiles(outDir)
  }

  /** What the generated tree depends on, as one line, readable on a shallow checkout WITHOUT the submodule's files: the engine artifact pinned in `project/plugins.sbt`, the libGDX commit (the
    * submodule's HEAD when it is initialised, else the commit this checkout records for it), this generator (line endings normalised, so every OS agrees) and the JDK feature version.
    */
  def fingerprint(llsRoot: Path): String = {
    def git(dir: Path, args: String*): Option[String] = {
      val pb = new ProcessBuilder(("git" +: args)*)
      pb.directory(dir.toFile)
      pb.redirectErrorStream(true)
      val p   = pb.start()
      val out = new String(p.getInputStream.readAllBytes()).trim
      if (p.waitFor() == 0 && out.nonEmpty) Some(out) else None
    }
    val pin = """balticporter-corpus" % "([^"]+)"""".r
      .findFirstMatchIn(Files.readString(llsRoot.resolve("project/plugins.sbt")))
      .map(_.group(1))
      .getOrElse(sys.error("[Baltic Porter] project/plugins.sbt pins no balticporter-corpus version"))
    val submodule = llsRoot.resolve("original-src/libgdx")
    val libgdx    = (if (Files.exists(submodule.resolve(".git"))) git(submodule, "rev-parse", "HEAD") else None)
      .orElse(git(llsRoot, "ls-tree", "HEAD", "original-src/libgdx").flatMap(_.split("\\s+").lift(2)))
      .getOrElse(
        sys.error("[Baltic Porter] cannot read the libGDX commit this checkout records (git ls-tree HEAD original-src/libgdx)")
      )
    val source    = Files.readString(llsRoot.resolve("project/BalticPorterGen.scala")).replace("\r", "")
    val generator = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes("UTF-8")).take(8).map(b => f"$b%02x").mkString
    // the JDK the generator runs on decides what a member overrides
    s"engine=$pin libgdx=$libgdx generator=$generator jdk=${java.lang.Runtime.version().feature()}"
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
