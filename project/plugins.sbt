// kubuszok plugin (bundles: sbt-git, sbt-scalafmt, sbt-scoverage, sbt-scalajs, sbt-scala-native, sbt-pgp, sbt-mima, sbt-ide-settings, sbt-commandmatrix)
// sbt 2.0 has projectMatrix built in; sbt-welcome has no sbt2 build and is no longer bundled.
addSbtPlugin("com.kubuszok" % "sbt-kubuszok" % "0.2.3")
// benchmarks
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.8")

// Baltic Porter: Java->Scala 3 porting engine, runs as a sourceGenerator. The policy it runs with is
// lls's own (lls-port/, compiled into this meta-build by project/build.sbt). The `lls-port` module in
// build.sbt reads this same pin.
resolvers += "Central Portal Snapshots" at "https://central.sonatype.com/repository/maven-snapshots"
libraryDependencies += "com.kubuszok" %% "balticporter-engine" % "61c1deb04ae429cf0cd8ce600a9676e0ab810694-SNAPSHOT"
