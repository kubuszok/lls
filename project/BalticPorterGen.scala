import sbt.*
import sbt.Keys.*

import java.nio.file.{ Files, Path }

/** sbt sourceGenerator that uses Baltic Porter to mechanically port the twelve libGDX utility sources lls carries into Scala 3.
  *
  * Requires:
  *   - libGDX sources at `original-src/libgdx/gdx/src` (git submodule)
  *   - the `balticporter-engine` artifact pinned in `project/plugins.sbt`
  *   - lls's porting policy, `lls-port/src/main/scala` (compiled into this meta-build by `project/build.sbt`)
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
      // sbt does not reload the build when lls-port/ changes; never generate with a policy older than the one on disk
      if (policyOnDisk(llsRoot) != LlsPortCompiled.digest)
        sys.error("[Baltic Porter] lls-port/src/main/scala changed after this build was loaded: run `reload`, then the command again.")
      val commit = balticporter.runner.VendoredCommit.of(libgdxSrc)
      log.info(s"[Baltic Porter] Generating lls sources from libGDX ($commit)")

      val manifest = lowlevel.port.LlsPolicy.core(lowlevel.port.LlsPolicy.DefaultRungs)

      val result = balticporter.runner
        .PortRun(
          label = "lls",
          portRoot = portRoot,
          sourceSet = balticporter.runner.SourceSet.Main,
          frontend = balticporter.core.FrontendConfig(
            libgdxSrc,
            lowlevel.port.LlsMigrate.Files,
            lowlevel.port.GdxCoreClasspath.entries(llsRoot.resolve("target/balticporter-classpath")),
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

  /** The engine version pinned in `project/plugins.sbt` — the one place it is written; the `lls-port` module depends on the same one. */
  def enginePin(llsRoot: Path): String =
    """balticporter-engine" % "([^"]+)"""".r
      .findFirstMatchIn(Files.readString(llsRoot.resolve("project/plugins.sbt")))
      .map(_.group(1))
      .getOrElse(sys.error("[Baltic Porter] project/plugins.sbt pins no balticporter-engine version"))

  /** The policy sources as they are on disk now, digested the way `project/build.sbt` digested them when it compiled them into this build. */
  private def policyOnDisk(llsRoot: Path): String = {
    val root  = llsRoot.resolve("lls-port/src/main/scala").toFile.getCanonicalFile
    val files = (root ** "*.scala").get().sortBy(_.getPath)
    sbt.io.Hash.toHex(sbt.io.Hash(files.map(f => sbt.io.Hash.toHex(sbt.io.Hash(f))).mkString))
  }

  /** One hash over the given files' relative paths and contents (line endings normalised, so every OS agrees), in path order. */
  private def sourceHash(root: Path, files: Seq[Path]): String = {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    files.sortBy(f => root.relativize(f).toString.replace('\\', '/')).foreach { f =>
      digest.update(root.relativize(f).toString.replace('\\', '/').getBytes("UTF-8"))
      digest.update(0.toByte)
      digest.update(Files.readString(f).replace("\r", "").getBytes("UTF-8"))
      digest.update(0.toByte)
    }
    digest.digest().take(8).map(b => f"$b%02x").mkString
  }

  /** What the generated tree depends on, as one line, readable on a shallow checkout WITHOUT the submodule's files: the engine artifact pinned in `project/plugins.sbt`, the libGDX commit (the
    * submodule's HEAD when it is initialised, else the commit this checkout records for it), this generator, the porting policy under `lls-port/src/main/scala` and the JDK feature version.
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
    val pin       = enginePin(llsRoot)
    val submodule = llsRoot.resolve("original-src/libgdx")
    val libgdx    = (if (Files.exists(submodule.resolve(".git"))) git(submodule, "rev-parse", "HEAD") else None)
      .orElse(git(llsRoot, "ls-tree", "HEAD", "original-src/libgdx").flatMap(_.split("\\s+").lift(2)))
      .getOrElse(
        sys.error("[Baltic Porter] cannot read the libGDX commit this checkout records (git ls-tree HEAD original-src/libgdx)")
      )
    val generator = sourceHash(llsRoot, Seq(llsRoot.resolve("project/BalticPorterGen.scala")))
    val policy    = sourceHash(llsRoot, collectScalaFiles(llsRoot.resolve("lls-port/src/main/scala")).map(_.toPath))
    // the JDK the generator runs on decides what a member overrides
    s"engine=$pin libgdx=$libgdx generator=$generator policy=$policy jdk=${java.lang.Runtime.version().feature()}"
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
