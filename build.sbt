import sbtwelcome.UsefulTask
import commandmatrix.extra.*
import kubuszok.sbt._
import kubuszok.sbt.KubuszokPlugin.autoImport._

// Versions

val versions = new {
  val scala3 = "3.8.3"

  val scalas    = List(scala3)
  val platforms = List(VirtualAxis.jvm, VirtualAxis.js, VirtualAxis.native)

  // Dependencies (test)
  val munit           = "1.3.2"
  val munitScalacheck = "1.3.0"
  val scalacheck      = "1.19.0"
}

val dev = new DevProperties(
  scala213 = None,
  scala3 = Some(versions.scala3),
  platforms = versions.platforms
)

lazy val al = new Aliases(
  published = Seq(lls),
  compileOnly = Seq(`lls-bench`)
)

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
    Test / unmanagedSourceDirectories += (Test / sourceDirectory).value / "scalajvm"
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
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .someVariations(versions.scalas, versions.platforms)((commonSettings ++ dev.only1VersionInIDE) *)
  .settings(
    name := "lls",
    description := "Low Level Scala — cross-platform, allocation-conscious utilities",
    libraryDependencies ++= Seq(
      "org.scalameta"  %%% "munit"            % versions.munit           % Test,
      "org.scalameta"  %%% "munit-scalacheck" % versions.munitScalacheck % Test,
      "org.scalacheck" %%% "scalacheck"       % versions.scalacheck      % Test
    )
  )
  .settings(publishSettings)
  .settings(mimaSettings)

lazy val `lls-bench` = (projectMatrix in file("lls-bench"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(versions.scala3))
  .enablePlugins(JmhPlugin)
  .someVariations(versions.scalas, List(VirtualAxis.jvm))((commonSettings ++ dev.only1VersionInIDE) *)
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
  .aggregate(`lls-bench`.projectRefs *)
  .settings(
    name := "lls-build",
    description := "Build setup for Low Level Scala",
    logo :=
      s"""Low Level Scala (lls) ${version.value} for Scala ${versions.scala3} x (Scala JVM, Scala.js $scalaJSVersion, Scala Native $nativeVersion)
         |
         |Cross-platform, allocation-conscious utilities: Nullable, MkArray, ArrayView,
         |DynamicArray, ObjectMap, ObjectSet, OrderedMap, OrderedSet, Sort, MathUtils.
         |
         |This build uses sbt-projectmatrix:
         | - Scala JVM adds no suffix to a project name seen in build.sbt
         | - Scala.js adds the "JS" suffix to a project name seen in build.sbt
         | - Scala Native adds the "Native" suffix to a project name seen in build.sbt
         |
         |When working with IntelliJ or Scala Metals, edit dev.properties to control which platform you're currently working with.
         |""".stripMargin,
    usefulTasks := al.usefulTasks(extra = Seq(
      UsefulTask("scalafmtAll", "Format all sources").noAlias
    ))
  )
  .settings(noPublishSettings)
  .settings(mimaSettings)
