/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: ported from ssg ssg-commons/src/main/scalanative/ssg/commons/io/FileOpsPlatform.scala
 * (the Native variant, ISS-977/1347) — deliberately, for the JVM too: the JVM thereby gains the
 * NoSuchFileException tolerance in deleteRecursively (within the documented "missing path is a
 * no-op" contract), removing the last JVM/Native body difference (plan §5). Identical body in
 * src/main/scalanative.
 */
package lowlevel
package io

import java.nio.file.{ Files, LinkOption, Path, StandardCopyOption }

private[io] object FileOpsPlatform {

  def readAllBytes(path: FilePath): Array[Byte] =
    Files.readAllBytes(FilePathNio.toNioPath(path))

  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit =
    Files.write(FilePathNio.toNioPath(path), bytes)

  def exists(path: FilePath): Boolean =
    Files.exists(FilePathNio.toNioPath(path))

  def isDirectory(path: FilePath): Boolean =
    Files.isDirectory(FilePathNio.toNioPath(path))

  def isRegularFile(path: FilePath): Boolean =
    Files.isRegularFile(FilePathNio.toNioPath(path))

  /** Last-modified time in milliseconds since the epoch (Files.getLastModifiedTime throws for a missing path). */
  def lastModifiedTime(path: FilePath): Long =
    Files.getLastModifiedTime(FilePathNio.toNioPath(path)).toMillis

  /** Immediate children of a directory, sorted by path string for deterministic output across platforms. Files.newDirectoryStream throws NotDirectoryException / NoSuchFileException for the non-dir /
    * missing cases, which propagate per the documented contract.
    */
  def list(path: FilePath): List[FilePath] = {
    val dir    = FilePathNio.toNioPath(path)
    val stream = Files.newDirectoryStream(dir)
    try {
      val builder = List.newBuilder[Path]
      stream.forEach(p => builder += p)
      builder.result().sortBy(_.toString).map(FilePathNio.fromNioPath)
    } finally
      stream.close()
  }

  /** Recursive pre-order descent. Each level is listed via [[list]] (already sorted), a directory entry is yielded before its contents, and directory symlinks are not descended into
    * (LinkOption.NOFOLLOW_LINKS keeps the is-directory test on the link itself, never its target).
    */
  def walkTree(path: FilePath): List[FilePath] = {
    val builder = List.newBuilder[FilePath]
    def descend(dir: FilePath): Unit =
      list(dir).foreach { child =>
        builder += child
        val nio = FilePathNio.toNioPath(child)
        if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) descend(child)
      }
    descend(path)
    builder.result()
  }

  /** Nested, idempotent directory creation (Files.createDirectories returns normally when the path already exists). */
  def createDirectories(path: FilePath): Unit =
    Files.createDirectories(FilePathNio.toNioPath(path)): Unit

  /** Byte-exact file copy; REPLACE_EXISTING overwrites a stale destination (rebuilds re-copy into a destination that may still hold a previous run's files).
    */
  def copy(from: FilePath, to: FilePath): Unit =
    Files.copy(
      FilePathNio.toNioPath(from),
      FilePathNio.toNioPath(to),
      StandardCopyOption.REPLACE_EXISTING
    ): Unit

  /** Removes a file or an entire tree; a missing path is a no-op. Symlinks are deleted as links — a directory symlink is removed directly (its target is never descended into), giving the clean-build
    * safety property (deleting a directory never reaches outside it through a link).
    *
    * Robustness (ported from ssg's Native variant, ISS-1347): if a path vanishes between the exists check and the delete (e.g. a concurrent deletion, or a javalib that follows a directory symlink
    * into a target that is itself being cleaned up), NoSuchFileException is caught and treated as a no-op — within the documented "a missing path is a no-op" contract.
    */
  def deleteRecursively(path: FilePath): Unit = {
    val nio = FilePathNio.toNioPath(path)
    if (Files.exists(nio, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) {
        list(path).foreach(deleteRecursively)
      }
      try Files.delete(nio)
      catch { case _: java.nio.file.NoSuchFileException => () }
    }
  }

  val isSupported: Boolean = true
}
