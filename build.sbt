import commandmatrix.extra.*
import kubuszok.sbt._
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions

// sbt 2.0's build dialect drops the structural-type refinement on `new { ... }`, so `versions.scala3`
// fails to resolve, and top-level objects aren't visible to lifted setting expressions either.
// Use plain top-level vals (the proven sbt-2.0 pattern).
val scala3 = "3.8.4"

val scalas    = List(scala3)
val platforms = List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)

// Dependencies (test)
// munit 1.3.3+: avoids the scala-native test-interface eviction with scala-native 0.5.12 on sbt 2.0.
val munitVersion           = "1.3.5"
val munitScalacheckVersion = "1.3.0"
val scalacheckVersion      = "1.20.0"

val dev = new DevProperties(
  scala213 = None,
  scala3 = Some(scala3),
  platforms = platforms
)

lazy val al = new Aliases(
  published = Seq(lls, `lls-io`),
  compileOnly = Seq(`lls-bench`)
)

// CI/test command aliases (e.g. ci-jvm-3, test-js-3, test-native-3), consumed by .github/workflows/ci.yml.
// sbt-welcome used to register these via `usefulTasks`, but it has no sbt 2.0 build, so we register
// them directly from the Aliases helper bundled with sbt-kubuszok.
def aliasName(prefix: String, platform: String, scalaBinary: String): String =
  s"$prefix-${platform.toLowerCase}-${scalaBinary.replace('.', '_')}"

// In sbt 2.0 the `test` task is incremental and machine-wide cached (it behaves like sbt 1.x's
// `testQuick`), so a CI run that begins with `clean` can still report "No tests to run" and pass
// vacuously. `testFull` is the uncached full run. Rewrite the generated `<id>/test` steps to
// `<id>/testFull` so CI always executes the whole suite.
def fullTests(command: String): String =
  command
    .split(";")
    .map { step =>
      val trimmed = step.trim
      if (trimmed.endsWith("/test")) trimmed.stripSuffix("/test") + "/testFull" else trimmed
    }
    .mkString(" ; ")

lazy val ciAliases: Seq[Def.Setting[?]] = {
  val platformNames = List("JVM", "JS", "Native")
  val scalaBinaries = List("3")
  val perCombination = for {
    platform    <- platformNames
    scalaBinary <- scalaBinaries
    setting <-
      addCommandAlias(aliasName("ci", platform, scalaBinary), fullTests(al.ci(platform, scalaBinary))) ++
        addCommandAlias(aliasName("test", platform, scalaBinary), fullTests(al.test(platform, scalaBinary)))
  } yield setting
  perCombination ++ addCommandAlias("ci-release", al.release)
}

val commonSettings = Seq(
  MatrixAction.ForAll.Configure(_.settings(
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-no-indent",
      "-Werror",
      "-Wimplausible-patterns",
      "-Wrecurse-with-default",
      "-Wenum-comment-discard",
      "-Wunused:imports,privates,locals,patvars,nowarn",
      "-Wconf:cat=deprecation:info"
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )),
  MatrixAction.ForPlatforms(VirtualAxis.jvm).Configure(_.settings(
    fork := true,
    Test / unmanagedSourceDirectories += (Test / sourceDirectory).value / "scalajvm",
    // sbt 2.0 + scoverage + `fork := true`: the scoverage runtime Invoker in the *forked* test JVM
    // writes per-thread measurement files into `<crossTarget>/scoverage-data`, but on sbt 2.0 that
    // directory is no longer pre-created in-process before the fork starts, so every instrumented
    // test crashes with `FileNotFoundException: .../scoverage-data/scoverage.measurements.*`. Ensure
    // the directory exists; piggy-back on Test/compile, which every test task depends on. Harmless
    // when coverage is off (the dir just stays empty).
    Test / compile := Def.uncached {
      val analysis = (Test / compile).value
      IO.createDirectory((Test / crossTarget).value / "scoverage-data")
      analysis
    }
  )),
  // Scala Native 0.5.12: munit 1.3.3 pulls test-interface 0.5.12 while scalacheck 1.19.0 still pulls
  // 0.5.8. Coursier already selects 0.5.12 (the newer toolchain version), but the Scala Native
  // plugin marks test-interface with a "strict" scheme, so the older request is reported as a
  // binary-incompat eviction error on sbt 2.0. Demote that eviction to a warning for native.
  MatrixAction.ForPlatforms(VirtualAxis.native).Configure(_.settings(
    evictionErrorLevel := sbt.util.Level.Warn
  ))
)

val publishSettings = Seq(
  organization := "com.kubuszok",
  homepage := Some(url("https://github.com/kubuszok/lls")),
  organizationHomepage := Some(url("https://kubuszok.com")),
  licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/kubuszok/lls/"),
      "scm:git:git@github.com:kubuszok/lls.git"
    )
  ),
  startYear := Some(2025),
  developers := List(
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://kubuszok.com"))
  ),
  pomExtra := (
    <issueManagement>
      <system>GitHub issues</system>
      <url>https://github.com/kubuszok/lls/issues</url>
    </issueManagement>
  ),
  projectType := ProjectType.ScalaLibrary
)

val noPublishSettings =
  Seq(projectType := ProjectType.NonPublished)

val mimaSettings = Seq(
  mimaPreviousArtifacts := Set(),
  mimaFailOnNoPrevious := false
)

// --- Modules ---

lazy val lls = (projectMatrix in file("lls"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(scala3))
  .someVariations(scalas, platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "lls",
    description := "Low Level Scala — cross-platform, allocation-conscious utilities",
    libraryDependencies ++= Seq(
      // sbt 2.0: %% is platform-aware (encodes Scala version + JS/Native suffix); %%% is gone.
      "org.scalameta"  %% "munit"            % munitVersion           % Test,
      "org.scalameta"  %% "munit-scalacheck" % munitScalacheckVersion % Test,
      "org.scalacheck" %% "scalacheck"       % scalacheckVersion      % Test
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)

lazy val `lls-io` = (projectMatrix in file("lls-io"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(scala3))
  .someVariations(scalas, platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "lls-io",
    description := "Low Level Scala — cross-platform immutable POSIX-string file paths and file operations",
    libraryDependencies ++= Seq(
      "org.scalameta"  %% "munit"            % munitVersion           % Test,
      "org.scalameta"  %% "munit-scalacheck" % munitScalacheckVersion % Test,
      "org.scalacheck" %% "scalacheck"       % scalacheckVersion      % Test
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)

lazy val `lls-bench` = (projectMatrix in file("lls-bench"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(scala3))
  .enablePlugins(JmhPlugin)
  .someVariations(scalas, List(VirtualAxis.jvm))((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "lls-bench",
    description := "JMH benchmarks for Low Level Scala",
    scalacOptions --= Seq("-Wunused:imports,privates,locals,patvars,nowarn")
  )
  .settings(noPublishSettings)
  .settings(mimaSettings)
  .dependsOn(lls)

// --- Root ---

lazy val root = (project in file("."))
  .enablePlugins(KubuszokRootPlugin)
  .aggregate(lls.projectRefs *)
  .aggregate(`lls-io`.projectRefs *)
  .aggregate(`lls-bench`.projectRefs *)
  .settings(
    name := "lls-build",
    description := "Build setup for Low Level Scala"
  )
  .settings(ciAliases)
  .settings(noPublishSettings)
  .settings(mimaSettings)
