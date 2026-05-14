// Format on compile during local development, skip on CI.
lazy val isCI = sys.env.get("CI").contains("true")
ThisBuild / packageDoc / publishArtifact := false
ThisBuild / scalafmtOnCompile := !isCI

// Version from git tags: tagged commits get clean versions (e.g. "0.1.0"),
// untagged commits get SNAPSHOT versions (e.g. "0.1.0-SNAPSHOT").
git.useGitDescribe       := true
git.uncommittedSignifier := Some("SNAPSHOT")
// Sonatype ignores isSnapshot setting and only looks at -SNAPSHOT suffix in version:
//   https://central.sonatype.org/publish/publish-maven/#performing-a-snapshot-deployment
// meanwhile sbt-git used to set up SNAPSHOT if there were uncommitted changes:
//   https://github.com/sbt/sbt-git/issues/164
// (now this suffix is empty by default) so we need to fix it manually.
git.gitUncommittedChanges := git.gitCurrentTags.value.isEmpty

// Used to publish snapshots to Maven Central.
val mavenCentralSnapshots = "Maven Central Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"

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
    Developer("MateuszKubuszok", "Mateusz Kubuszok", "", url("https://github.com/MateuszKubuszok"))
  ),
  pomExtra := (
    <issueManagement>
      <system>GitHub issues</system>
      <url>https://github.com/kubuszok/lls/issues</url>
    </issueManagement>
  ),
  publishTo := {
    if (isSnapshot.value) Some(mavenCentralSnapshots)
    else localStaging.value
  },
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ =>
    false
  },
  versionScheme := Some("early-semver")
)

val noPublishSettings =
  Seq(publish / skip := true, publishArtifact := false)

// --- MiMa settings ---

val mimaSettings = Seq(
  mimaPreviousArtifacts := Set(),
  mimaFailOnNoPrevious := false
)

// --- Modules ---

lazy val root = project
  .in(file("."))
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(LlsSettings.commonSettings *)
  .settings(publishSettings *)
  .settings(noPublishSettings *)
  .aggregate(lls.projectRefs *)
  .aggregate(`lls-bench`.projectRefs *)
  .settings(
    moduleName := "lls-build",
    name := "lls-build",
    description := "Build setup for Low Level Scala"
  )

lazy val lls = (projectMatrix in file("lls"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(LlsSettings.scalaVersion))
  .enablePlugins(GitVersioning, GitBranchPrompt)
  .settings(LlsSettings.commonSettings *)
  .settings(publishSettings *)
  .settings(mimaSettings *)
  .settings(
    moduleName := "lls",
    name := "lls",
    description := "Low Level Scala — cross-platform, allocation-conscious utilities",
    libraryDependencies ++= Seq(
      "org.scalameta"  %%% "munit"            % LlsSettings.versions.munit           % Test,
      "org.scalameta"  %%% "munit-scalacheck" % LlsSettings.versions.munitScalacheck % Test,
      "org.scalacheck" %%% "scalacheck"       % LlsSettings.versions.scalacheck      % Test
    )
  )
  .jvmPlatform(scalaVersions = Seq(LlsSettings.scalaVersion), settings = LlsSettings.jvmSettings)
  .jsPlatform(scalaVersions = Seq(LlsSettings.scalaVersion), settings = LlsSettings.jsSettings)
  .nativePlatform(scalaVersions = Seq(LlsSettings.scalaVersion), settings = LlsSettings.nativeSettings)

lazy val `lls-bench` = (projectMatrix in file("lls-bench"))
  .defaultAxes(VirtualAxis.jvm, VirtualAxis.scalaABIVersion(LlsSettings.scalaVersion))
  .enablePlugins(JmhPlugin)
  .settings(LlsSettings.commonSettings *)
  .settings(noPublishSettings *)
  .settings(
    moduleName := "lls-bench",
    name := "lls-bench",
    description := "JMH benchmarks for Low Level Scala",
    scalacOptions --= Seq("-Wunused:imports,privates,locals,patvars,nowarn")
  )
  .dependsOn(lls)
  .jvmPlatform(scalaVersions = Seq(LlsSettings.scalaVersion), settings = LlsSettings.jvmSettings)

// --- CI command aliases ---

addCommandAlias("test-coverage", "; coverage; lls/test; coverageAggregate; coverageOff")
addCommandAlias("test-js", "llsJS/test")
addCommandAlias("test-native", "llsNative/test")
addCommandAlias("ci-release", "publishSigned")
