/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: nio bridge extracted from ssg
 *   ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala (toNioPath/fromNioPath,
 *   ISS-1128), extended with the UNC pass-through rule (plan §4.4). The scalanative variant has
 *   Windows UNC workarounds the JVM does not need (Scala Native's NIO may not fully preserve the
 *   UNC prefix through toString on Windows).
 */
package lowlevel
package io

import java.nio.file.{ Path, Paths }

/** Public bridge between the POSIX-string [[FilePath]] model and `java.nio.file.Path` (JVM and Native only; there is no Scala.js variant — JS consumers use `pathString` directly).
  */
object FilePathNio {

  private val isWindows: Boolean =
    sys.props.getOrElse("os.name", "").toLowerCase.contains("windows")

  /** Unwraps to a `java.nio.file.Path` for FS I/O.
    *
    * On Windows, strips the model's leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so nio parses the drive letter (java.nio.Paths.get rejects a leading slash before a drive); a no-op on
    * POSIX-form paths. On Scala Native Windows, a UNC model path ("//host/share/x") is converted to the native backslash form so the OS recognizes it as UNC; on POSIX, it passes through unchanged
    * (the OS treats the doubled leading slash as "/", harmless).
    */
  def toNioPath(path: FilePath): Path =
    Paths.get(toNioString(path.pathString))

  /** Wraps a `java.nio.file.Path` as a [[FilePath]], POSIX-rendering the string so children from directory listing match `dir.resolve(name)`.
    *
    * On linux/macOS `path.toString` is already '/'-separated so the replace is a no-op and rendering is idempotent; on Windows it converts backslash separators to forward slashes. A Windows-absolute
    * nio path ("C:/Users/foo") is not POSIX-absolute after rendering; it is prefixed with '/' so the model form is absolute and round-trips via [[toNioPath]] (no-op on POSIX). A Windows UNC path
    * ("\\h\s\x") becomes "//h/s/x" and the renderer's exactly-two-leading-slashes rule preserves the UNC marker (plan §4.4).
    *
    * On Scala Native Windows, `Path.toString` may drop the UNC host/share prefix. When `getRoot` indicates a UNC root, the full path is reconstructed from root + name components.
    */
  def fromNioPath(path: Path): FilePath = {
    val str      = reconstructUncIfNeeded(path)
    val rendered = FilePath.renderPath(str)
    new FilePath(if (path.isAbsolute && !rendered.startsWith("/")) "/" + rendered else rendered)
  }

  /** Translates the FilePath model form to a string `java.nio.Paths.get` accepts: strips the leading '/' from a drive-absolute path ("/C:/x" -> "C:/x") so Windows nio parses the drive. On Scala
    * Native Windows, converts UNC model form ("//h/s/x") to native backslash form ("\\h\s\x") because the native NIO may not recognise forward-slash UNC paths.
    */
  private def toNioString(p: String): String =
    if (FilePath.isDriveAbsolute(p)) p.substring(1)
    else if (isWindows && FilePath.isUncAbsolute(p)) p.replace('/', '\\')
    else p

  /** On Scala Native Windows, `Path.toString` for a UNC path may lose the host and share segments. This method detects that case via `getRoot` and reconstructs the full forward-slash path from root +
    * name components. On POSIX or when the root is not UNC, falls back to the plain `toString.replace` path.
    */
  private def reconstructUncIfNeeded(path: Path): String = {
    if (!isWindows) return path.toString.replace('\\', '/')
    val root = path.getRoot
    if (root == null) return path.toString.replace('\\', '/')
    val rootStr = root.toString.replace('\\', '/')
    // A UNC root on Windows looks like "//host/share/" or "\\host\share\" — after replace it starts with "//"
    if (rootStr.startsWith("//")) {
      val names    = (0 until path.getNameCount).map(i => path.getName(i).toString.replace('\\', '/'))
      val rootNorm = rootStr.stripSuffix("/")
      if (names.isEmpty) rootNorm else rootNorm + "/" + names.mkString("/")
    } else {
      path.toString.replace('\\', '/')
    }
  }
}
