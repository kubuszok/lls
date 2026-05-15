// linters
addSbtPlugin("org.scalameta"    % "sbt-scalafmt"      % "2.5.4")
// coverage
addSbtPlugin("org.scoverage"    % "sbt-scoverage"     % "2.4.4")
// cross-compilation
addSbtPlugin("com.eed3si9n"     % "sbt-projectmatrix" % "0.11.0")
addSbtPlugin("org.scala-js"     % "sbt-scalajs"       % "1.21.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native"  % "0.5.11")
// git versioning
addSbtPlugin("com.github.sbt"  % "sbt-git"           % "2.1.0")
// publishing
addSbtPlugin("com.github.sbt"  % "sbt-pgp"           % "2.3.1")
// binary compatibility
addSbtPlugin("com.typesafe"    % "sbt-mima-plugin"   % "1.1.5")
// benchmarks
addSbtPlugin("pl.project13.scala" % "sbt-jmh"        % "0.4.8")
// IDE
addSbtPlugin("org.jetbrains"      % "sbt-ide-settings" % "1.1.0")
// welcome
addSbtPlugin("com.github.reibitto" % "sbt-welcome"     % "0.5.0")
