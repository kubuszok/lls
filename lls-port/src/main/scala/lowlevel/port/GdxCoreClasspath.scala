package lowlevel.port

import balticporter.runner.ClasspathCache

import java.nio.file.Path

/** The libGDX core jar at the vendored tree's version: the port reads a SUBSET of `gdx/src`, and the types that subset mentions outside itself resolve from this jar's class files. Resolved once with
  * `cs` and cached under the directory the caller names.
  */
object GdxCoreClasspath:
  val Coordinates:              List[String] = List("com.badlogicgames.gdx:gdx:1.14.1")
  def cache(cacheRoot: Path):   Path         = cacheRoot.resolve("out/gdx-core-classpath.txt")
  def entries(cacheRoot: Path): List[Path]   =
    ClasspathCache.entries(cache(cacheRoot), "gdx-core", Coordinates)
