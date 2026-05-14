import sbt.*
import sbt.Keys.*

object LlsSettings {

  val scalaVersion = "3.8.3"

  object versions {
    val munit           = "1.3.0"
    val munitScalacheck = "1.0.0"
    val scalacheck      = "1.19.0"
  }

  val commonSettings: Seq[Setting[?]] = Seq(
    Keys.scalaVersion := scalaVersion,
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
  )

  val jvmSettings: Seq[Setting[?]] = Seq(
    fork := true,
    Test / unmanagedSourceDirectories += (Test / sourceDirectory).value / "scalajvm"
  )

  val jsSettings: Seq[Setting[?]] = Seq.empty

  val nativeSettings: Seq[Setting[?]] = Seq.empty
}
