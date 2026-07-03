/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: pure path ops extracted from ssg
 *   ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala
 * (ISS-1128/980/982/1127/1182/1346), unified into a single shared final class,
 * extended with the UNC rules (plan §4.4) and the drive/UNC normalize floors (plan §4.5).
 */
package lowlevel
package io

/** Immutable, OS-independent file path in POSIX string form ('/'-separated on every OS; Windows drive-absolute paths are modelled as "/C:/x", UNC paths as "//host/share/x").
  */
final class FilePath private[io] (val pathString: String) {

  def parent: Option[FilePath] = {
    val dir = FilePath.posixDirname(pathString)
    if (dir == pathString) None
    else if (dir == "." && !pathString.contains('/')) None
    else Some(new FilePath(dir))
  }

  def resolve(other: String): FilePath =
    if (other.isEmpty) this
    else if (other.startsWith("/")) new FilePath(FilePath.renderPath(other))
    else {
      val sep = if (pathString.endsWith("/")) "" else "/"
      new FilePath(pathString + sep + other)
    }

  def resolve(other: FilePath): FilePath =
    resolve(other.pathString)

  def fileName: String =
    FilePath.posixBasename(pathString)

  def isAbsolute: Boolean =
    pathString.startsWith("/")

  def toAbsolute: FilePath =
    if (isAbsolute) this
    else FilePath.cwd.resolve(pathString)

  def normalize: FilePath =
    new FilePath(FilePath.posixNormalize(pathString))

  override def toString: String = pathString

  override def hashCode(): Int = pathString.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: FilePath => pathString == other.pathString
    case _ => false
  }
}

object FilePath {

  /** The model separator. Always '/': the model is POSIX-string on every OS. */
  val separatorChar: Char = '/'

  def of(path: String): FilePath =
    new FilePath(renderPath(path))

  def cwd: FilePath =
    of(FilePathCompat.cwdString)

  /** True if `p` is the POSIX-rendered form of a Windows drive-absolute path: "/X:/...". */
  private[io] def isDriveAbsolute(p: String): Boolean =
    p.length >= 3 && p.charAt(0) == '/' && p.charAt(2) == ':' && {
      val c = p.charAt(1); (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
    }

  /** True if `p` is a UNC path in model form: "//host..." (exactly two leading slashes). */
  private[io] def isUncAbsolute(p: String): Boolean =
    p.length >= 3 && p.charAt(0) == '/' && p.charAt(1) == '/' && p.charAt(2) != '/'

  /** True if `p` is a NATIVE Windows drive-absolute input: a single ASCII drive letter, ':', then a separator ('/' or '\\') — "C:/a" or "C:\a". Such inputs are lifted into the model form "/C:/a" by
    * [[renderPath]] so `isAbsolute` holds and `toAbsolute` never string-joins cwd onto an already-absolute host path (the ssg ISS-1383 defect class; the same lift cwd and the nio bridge already
    * apply). The separator requirement keeps the ISS-1128 contract intact: "a:b" (no single drive letter) and "C:name" (no separator — drive-relative on Windows) remain ordinary relative filenames.
    */
  private[io] def isNativeDriveAbsolute(p: String): Boolean =
    p.length >= 3 && p.charAt(1) == ':' && (p.charAt(2) == '/' || p.charAt(2) == '\\') && {
      val c = p.charAt(0); (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
    }

  /** Pure-string rendering (plan §4.1). Collapses duplicate separators, preserves a leading exactly-two-slash UNC marker, drops a trailing '/', preserves backslash and "."/".." segments — EXCEPT for
    * a native Windows drive-absolute input ("C:/a" / "C:\a"), which is first canonicalized into the model form ("/C:/a", backslash separators converted), because leaving it unlifted would make
    * `isAbsolute` false for an absolute host path and `toAbsolute` would then string-join cwd onto it (the ssg ISS-1383 defect class; documented bug-fix-class deviation from ssg's renderPath).
    */
  private[io] def renderPath(p0: String): String = {
    if (p0.isEmpty) return ""
    val p    = if (isNativeDriveAbsolute(p0)) "/" + p0.replace('\\', '/') else p0
    val unc  = isUncAbsolute(p)
    val abs  = p.startsWith("/")
    val body = p.split("/").iterator.filter(_.nonEmpty).mkString("/")
    if (unc) {
      if (body.isEmpty) "/" else "//" + body
    } else if (abs) {
      if (body.isEmpty) "/" else "/" + body
    } else body
  }

  /** POSIX dirname, with UNC-root floors (plan §4.4). */
  private[io] def posixDirname(p: String): String =
    if (isUncAbsolute(p)) {
      // Segments after the "//" marker.
      val segs = p.substring(2).split("/").iterator.filter(_.nonEmpty).toArray
      if (segs.length <= 2) p // "//h" or "//h/s" are their own floor
      else "//" + segs.dropRight(1).mkString("/")
    } else {
      val idx = p.lastIndexOf('/')
      if (idx < 0) "."
      else if (idx == 0) "/"
      else p.substring(0, idx)
    }

  /** POSIX basename, with UNC-root floors (plan §4.4). */
  private[io] def posixBasename(p: String): String =
    if (isUncAbsolute(p)) {
      val segs = p.substring(2).split("/").iterator.filter(_.nonEmpty).toArray
      if (segs.length <= 2) "" // UNC root (like "/")
      else segs.last
    } else {
      val idx = p.lastIndexOf('/')
      if (idx < 0) p
      else if (idx == p.length - 1) ""
      else p.substring(idx + 1)
    }

  /** Pure-Scala POSIX dot-segment normalization matching `java.nio.file.Path.normalize` on plain POSIX paths, extended with two host-independent floor rules the JVM nio delegation lacks (plan
    * §4.4/§4.5):
    *
    *   - Drive-absolute paths ("/X:/..."): the drive segment "X:" is a floor — ".." can never remove it (`normalize("/C:/a/../..") == "/C:"`).
    *   - UNC paths ("//host/share/..."): the root component is a floor. The root is the first up-to-two leading real (non-"."/"..") segments — "//host/share" when at least two exist, else "//host" —
    *     mirroring the parent/fileName root rule; ".." never rises above it and the "//" marker is never lost (`normalize("//h/s/a/../..") == "//h/s"`, `normalize("//h/..") == "//h"`).
    *
    * The floor is derived from LEADING real segments so a "." / ".." occurring where the share would be does not count as a root component. Plain absolute paths keep POSIX behavior (".." above "/"
    * dropped); relative paths keep leading ".." and a fully-reduced path renders as "" (matching `Paths.get(".").normalize().toString == ""`).
    */
  private[io] def posixNormalize(p: String): String =
    if (p.isEmpty) ""
    else {
      val unc = isUncAbsolute(p)
      val abs = p.startsWith("/")
      // Body without the leading marker; empty segments collapse duplicate separators.
      val body = if (unc) p.substring(2) else if (abs) p.substring(1) else p
      val segs = body.split("/").iterator.filter(_.nonEmpty).toArray
      // Floor segment count: never removable by "..". Drive => the single "X:" segment; UNC => the first
      // up-to-two LEADING real segments (host[/share]); plain paths have no floor.
      val floorCount: Int =
        if (unc) {
          var n = 0
          while (n < segs.length && n < 2 && segs(n) != "." && segs(n) != "..") n += 1
          n
        } else if (isDriveAbsolute(p)) 1
        else 0
      val floorSegs = segs.take(floorCount)
      val rest      = segs.drop(floorCount)

      val stack = new scala.collection.mutable.ArrayBuffer[String](rest.length)
      var i     = 0
      while (i < rest.length) {
        val seg = rest(i)
        if (seg == ".") {
          // no-op
        } else if (seg == "..") {
          if (stack.nonEmpty && stack.last != "..") {
            stack.remove(stack.length - 1): Unit
          } else if (!abs) {
            stack += ".."
          }
          // absolute: ".." at/above the floor is dropped
        } else {
          stack += seg
        }
        i += 1
      }

      val prefix  = if (unc) "//" else if (abs) "/" else ""
      val allSegs = floorSegs ++ stack
      if (allSegs.isEmpty) if (abs) "/" else ""
      else prefix + allSegs.mkString("/")
    }
}
