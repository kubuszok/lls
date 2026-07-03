/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: nio bridge extracted from ssg
 *   ssg-commons/src/main/scalajvm/ssg/commons/io/FilePathPlatform.scala (toNioPath/fromNioPath,
 *   ISS-1128), extended with the UNC pass-through rule (plan §4.4). Identical body in
 *   src/main/scalanative (plan §3.3).
 */
package lowlevel
package io

import java.nio.file.{ Path, Paths }

/** Public bridge between the POSIX-string [[FilePath]] model and `java.nio.file.Path` (JVM and Native only; there is no Scala.js variant — JS consumers use `pathString` directly).
  */
object FilePathNio {

  /** Unwraps to a `java.nio.file.Path` for FS I/O.
    *
    * On Windows, strips the model's leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so nio parses the drive letter (java.nio.Paths.get rejects a leading slash before a drive); a no-op on
    * POSIX-form paths. A UNC model path ("//host/share/x") passes through unchanged: `Paths.get("//h/s/x")` parses as UNC on Windows, and on POSIX the OS treats the doubled leading slash as "/"
    * (harmless).
    */
  def toNioPath(path: FilePath): Path =
    Paths.get(toNioString(path.pathString))

  /** Wraps a `java.nio.file.Path` as a [[FilePath]], POSIX-rendering the string so children from directory listing match `dir.resolve(name)`.
    *
    * On linux/macOS `path.toString` is already '/'-separated so the replace is a no-op and rendering is idempotent; on Windows it converts backslash separators to forward slashes. A Windows-absolute
    * nio path ("C:/Users/foo") is not POSIX-absolute after rendering; it is prefixed with '/' so the model form is absolute and round-trips via [[toNioPath]] (no-op on POSIX). A Windows UNC path
    * ("\\h\s\x") becomes "//h/s/x" and the renderer's exactly-two-leading-slashes rule preserves the UNC marker (plan §4.4).
    */
  def fromNioPath(path: Path): FilePath = {
    val rendered = FilePath.renderPath(path.toString.replace('\\', '/'))
    new FilePath(if (path.isAbsolute && !rendered.startsWith("/")) "/" + rendered else rendered)
  }

  /** Translates the FilePath model form to a string `java.nio.Paths.get` accepts: strips the leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so Windows nio parses the drive. Unchanged for
    * every other form, including UNC ("//h/s/x").
    */
  private def toNioString(p: String): String =
    if (FilePath.isDriveAbsolute(p)) p.substring(1) else p
}
