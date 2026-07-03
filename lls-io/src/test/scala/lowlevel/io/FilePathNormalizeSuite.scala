/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from the ssg suites FilePathIss980NativeSuite, FilePathIss980ExtraNativeSuite,
 *   FilePathIss1127JsSuite, FilePathIss1127ExtraJsSuite, FilePathIss1182Suite and the normalize /
 *   parent / fileName / resolve / toAbsolute / cwd sections of FilePathIss982Suite. Those were
 *   platform-specific (JS-only / Native-only) pinning JVM-parity values; here the shared pure
 *   resolver defines those semantics, so every case runs on all three platforms. Adds the §4.5
 *   drive-root normalize floor. Every expected value is the java.nio.file.Path result on POSIX JVM.
 */
package lowlevel
package io

final class FilePathNormalizeSuite extends munit.FunSuite {

  // ===== normalize: dot-segment resolution =========================================================

  test("normalize keeps an absolute path absolute — /a/../b becomes /b") {
    assertEquals(FilePath.of("/a/../b").normalize.pathString, "/b")
  }

  test("normalize removes a single-dot segment without dropping the root — /a/./b becomes /a/b") {
    assertEquals(FilePath.of("/a/./b").normalize.pathString, "/a/b")
  }

  test("normalize keeps a relative path relative — a/../b becomes b") {
    assertEquals(FilePath.of("a/../b").normalize.pathString, "b")
  }

  test("normalize removes a single-dot segment on a relative path — a/./b becomes a/b") {
    assertEquals(FilePath.of("a/./b").normalize.pathString, "a/b")
  }

  test("normalize of /.. collapses to the root /") {
    assertEquals(FilePath.of("/..").normalize.pathString, "/")
  }

  test("normalize of /../.. collapses to the root /") {
    assertEquals(FilePath.of("/../..").normalize.pathString, "/")
  }

  test("normalized absolute path reports isAbsolute") {
    assert(FilePath.of("/a/../b").normalize.isAbsolute)
  }

  test("normalize of the literal \".\" is the empty string") {
    assertEquals(FilePath.of(".").normalize.pathString, "")
  }

  test("normalize of \"a/..\" is the empty string") {
    assertEquals(FilePath.of("a/..").normalize.pathString, "")
  }

  test("normalize of \"./.\" is the empty string") {
    assertEquals(FilePath.of("./.").normalize.pathString, "")
  }

  test("normalize strips a trailing separator on a relative path — a/b/ becomes a/b") {
    assertEquals(FilePath.of("a/b/").normalize.pathString, "a/b")
  }

  test("normalize of \"./a\" drops the leading dot — becomes a") {
    assertEquals(FilePath.of("./a").normalize.pathString, "a")
  }

  test("normalize of \"..\" stays \"..\"") {
    assertEquals(FilePath.of("..").normalize.pathString, "..")
  }

  test("normalize of \"../..\" stays \"../..\"") {
    assertEquals(FilePath.of("../..").normalize.pathString, "../..")
  }

  test("normalize of a relative path that escapes upward — foo/../../bar becomes ../bar") {
    assertEquals(FilePath.of("foo/../../bar").normalize.pathString, "../bar")
  }

  test("normalize of a non-reducing relative path is unchanged — a/b stays a/b") {
    assertEquals(FilePath.of("a/b").normalize.pathString, "a/b")
  }

  test("normalize resolves two levels — /a/b/c/../../d becomes /a/d") {
    assertEquals(FilePath.of("/a/b/c/../../d").normalize.pathString, "/a/d")
  }

  test("normalize of /a/b/../c is /a/c") {
    assertEquals(FilePath.of("/a/b/../c").normalize.pathString, "/a/c")
  }

  // ===== §4.5 drive-root normalize floor ===========================================================

  test("normalize floors at the drive root — /C:/a/../.. becomes /C:") {
    assertEquals(FilePath.of("/C:/a/../..").normalize.pathString, "/C:")
  }

  test("normalize floors .. at the drive root — /C:/.. becomes /C:") {
    assertEquals(FilePath.of("/C:/..").normalize.pathString, "/C:")
  }

  test("normalize below the drive root resolves normally — /C:/a/b/../c becomes /C:/a/c") {
    assertEquals(FilePath.of("/C:/a/b/../c").normalize.pathString, "/C:/a/c")
  }

  test("normalize of a plain absolute path floors at / — /a/../../.. becomes /") {
    assertEquals(FilePath.of("/a/../../..").normalize.pathString, "/")
  }

  // ===== parent ====================================================================================

  test("parent of /a is the root Some(/)") {
    assertEquals(FilePath.of("/a").parent.map(_.pathString), Some("/"))
  }

  test("parent of the root / is None") {
    assertEquals(FilePath.of("/").parent, None)
  }

  test("parent of a bare relative name is None") {
    assertEquals(FilePath.of("a").parent, None)
  }

  test("parent of /a/b/c.txt is /a/b") {
    assertEquals(FilePath.of("/a/b/c.txt").parent.map(_.pathString), Some("/a/b"))
  }

  test("parent of a/b is a") {
    assertEquals(FilePath.of("a/b").parent.map(_.pathString), Some("a"))
  }

  // ===== fileName ==================================================================================

  test("fileName of an absolute path is the last component") {
    assertEquals(FilePath.of("/a/b/c.txt").fileName, "c.txt")
  }

  test("fileName of the root / is the empty string") {
    assertEquals(FilePath.of("/").fileName, "")
  }

  test("fileName of a bare relative name is that name") {
    assertEquals(FilePath.of("hello.txt").fileName, "hello.txt")
  }

  test("fileName of a relative path is the last component") {
    assertEquals(FilePath.of("a/b/c").fileName, "c")
  }

  // ===== resolve(String) ===========================================================================

  test("resolve of a simple relative child appends") {
    assertEquals(FilePath.of("/usr").resolve("local").pathString, "/usr/local")
  }

  test("resolve of an absolute child replaces the base") {
    assertEquals(FilePath.of("/a/b").resolve("/x/y").pathString, "/x/y")
  }

  test("resolve of an empty child returns this") {
    val base = FilePath.of("/a/b")
    assertEquals(base.resolve("").pathString, "/a/b")
  }

  test("resolve renders a non-canonical absolute child — resolve(\"///a\") yields /a") {
    assertEquals(FilePath.of("/base").resolve("///a").pathString, "/a")
  }

  test("resolve then normalize on an absolute base matches java.nio") {
    assertEquals(FilePath.of("/base/dir").resolve("../x/./y").normalize.pathString, "/base/x/y")
    assertEquals(FilePath.of("/").resolve("a/b").normalize.pathString, "/a/b")
    assertEquals(FilePath.of("/a/b").resolve("c/../d").normalize.pathString, "/a/b/d")
  }

  // ===== resolve(FilePath) =========================================================================

  test("resolve(FilePath) with a relative child appends") {
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("c/d")).pathString, "/a/b/c/d")
  }

  test("resolve(FilePath) with an absolute child replaces") {
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("/x/y")).pathString, "/x/y")
  }

  // ===== toAbsolute ================================================================================

  test("toAbsolute leaves an already-absolute path unchanged") {
    assertEquals(FilePath.of("/etc/hosts").toAbsolute.pathString, "/etc/hosts")
  }

  test("toAbsolute of a relative path yields an absolute path") {
    assert(FilePath.of("rel/file.txt").toAbsolute.isAbsolute)
    assert(FilePath.of("relative").toAbsolute.isAbsolute)
    assert(FilePath.of("a/b/c").toAbsolute.isAbsolute)
  }

  test("toAbsolute of a relative path resolves against cwd (append semantics)") {
    // Pins the exact behavior without hard-coding the environment's cwd: toAbsolute == cwd.resolve(_).
    assertEquals(FilePath.of("rel/file.txt").toAbsolute.pathString, FilePath.cwd.resolve("rel/file.txt").pathString)
  }

  test("toAbsolute does NOT normalize — a/../b keeps the .. (matches java.nio.toAbsolutePath)") {
    val abs = FilePath.of("a/../b").toAbsolute
    assert(abs.isAbsolute)
    assert(abs.pathString.endsWith("/a/../b"), abs.pathString)
    assertEquals(abs.pathString, FilePath.cwd.resolve("a/../b").pathString)
  }

  // ===== cwd =======================================================================================

  test("cwd is absolute") {
    assert(FilePath.cwd.isAbsolute)
  }

  test("cwd is not the literal dot") {
    assertNotEquals(FilePath.cwd.pathString, ".")
  }

  test("cwd pathString is non-empty") {
    assert(FilePath.cwd.pathString.nonEmpty)
  }

  // ===== combined / round-trip =====================================================================

  test("resolve then parent round-trips") {
    val resolved = FilePath.of("/a").resolve("b")
    assertEquals(resolved.pathString, "/a/b")
    assertEquals(resolved.parent.map(_.pathString), Some("/a"))
  }

  test("normalize then parent — /a/b/../c normalizes to /a/c, parent is /a") {
    val normalized = FilePath.of("/a/b/../c").normalize
    assertEquals(normalized.pathString, "/a/c")
    assertEquals(normalized.parent.map(_.pathString), Some("/a"))
  }

  test("resolve then fileName — last component of resolved path") {
    assertEquals(FilePath.of("/usr").resolve("local/bin").fileName, "bin")
  }

  test("toAbsolute of an absolute path preserves fileName") {
    assertEquals(FilePath.of("/a/b/c.txt").toAbsolute.fileName, "c.txt")
  }
}
