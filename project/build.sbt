// lls's porting policy (lls-port/) is compiled into the meta-build, so the source generator calls it
// directly; the same directory is the published `lls-port` module in ../build.sbt.
Compile / unmanagedSourceDirectories += baseDirectory.value / ".." / "lls-port" / "src" / "main" / "scala"

// Editing lls-port/ does not make sbt reload the build, so the generator would run the OLD compiled
// policy. Record which sources this meta-build was compiled from; BalticPorterGen compares and asks
// for a `reload`. The digest expression is the same one as BalticPorterGen.policyOnDisk.
Compile / sourceGenerators += Def.task {
  val root   = (baseDirectory.value / ".." / "lls-port" / "src" / "main" / "scala").getCanonicalFile
  val files  = (root ** "*.scala").get().sortBy(_.getPath)
  val digest = sbt.io.Hash.toHex(sbt.io.Hash(files.map(f => sbt.io.Hash.toHex(sbt.io.Hash(f))).mkString))
  val out    = (Compile / sourceManaged).value / "LlsPortCompiled.scala"
  IO.write(out, s"""object LlsPortCompiled { val digest: String = "$digest" }\n""")
  Seq(out)
}
