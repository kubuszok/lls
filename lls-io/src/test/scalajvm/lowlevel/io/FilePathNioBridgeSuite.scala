/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from the round-trip parts of ssg FilePathWin32ParityIss1128Suite, extended
 * with the UNC bridge rules (plan §3.3/§4.4). String-level round-trips only — no actual network
 * share is touched. Host-agnostic assertions run everywhere; Windows-host assertions are guarded
 * to execute on the Windows CI legs (both JVM and Native have windows-latest legs), POSIX-host
 * assertions on the rest. The suite text is identical in scalajvm and scalanative.
 */
package lowlevel
package io

import java.nio.file.Paths

final class FilePathNioBridgeSuite extends munit.FunSuite {

  private val isWindows: Boolean =
    sys.props.getOrElse("os.name", "").toLowerCase.contains("windows")

  // ===== host-agnostic: toNioPath translation ======================================================

  test("toNioPath strips the model's leading / from a drive-absolute path — /C:/x parses as C:/x") {
    assertEquals(FilePathNio.toNioPath(FilePath.of("/C:/x")), Paths.get("C:/x"))
  }

  test("toNioPath passes a plain absolute path through unchanged") {
    assertEquals(FilePathNio.toNioPath(FilePath.of("/a/b")), Paths.get("/a/b"))
  }

  test("toNioPath passes a relative path through unchanged") {
    assertEquals(FilePathNio.toNioPath(FilePath.of("a/b")), Paths.get("a/b"))
  }

  test("toNioPath passes the UNC model form through unchanged (no stripping applied)") {
    // On Windows Paths.get("//h/s/x") parses as UNC; on POSIX the OS treats the doubled slash as "/"
    // (Paths.get("//h/s/x") == Paths.get("/h/s/x")) — harmless per plan §4.4. Either way the bridge
    // must hand nio exactly the model string.
    assertEquals(FilePathNio.toNioPath(FilePath.of("//h/s/x")), Paths.get("//h/s/x"))
  }

  // ===== host-agnostic: fromNioPath rendering ======================================================

  test("fromNioPath POSIX-renders a plain absolute nio path") {
    assertEquals(FilePathNio.fromNioPath(Paths.get("/a/b")).pathString, "/a/b")
  }

  test("fromNioPath POSIX-renders a relative nio path") {
    assertEquals(FilePathNio.fromNioPath(Paths.get("a/b")).pathString, "a/b")
  }

  test("fromNioPath round-trips a relative path") {
    val p = FilePath.of("rel/dir/file.txt")
    assertEquals(FilePathNio.fromNioPath(FilePathNio.toNioPath(p)), p)
  }

  test("fromNioPath round-trips a plain absolute path") {
    val p = FilePath.of("/a/b/c.txt")
    assertEquals(FilePathNio.fromNioPath(FilePathNio.toNioPath(p)), p)
  }

  // ===== Windows-host assertions (execute on the Windows CI legs) ==================================

  test("Windows: fromNioPath restores the / prefix on a drive-absolute path") {
    assume(isWindows, "drive-absolute nio parsing requires a Windows host")
    assertEquals(FilePathNio.fromNioPath(Paths.get("C:\\Users\\foo")).pathString, "/C:/Users/foo")
  }

  test("Windows: drive-absolute model form round-trips through nio") {
    assume(isWindows, "drive-absolute nio parsing requires a Windows host")
    val p = FilePath.of("/C:/Users/foo/file.txt")
    assertEquals(FilePathNio.fromNioPath(FilePathNio.toNioPath(p)), p)
  }

  test("Windows: fromNioPath preserves the UNC marker — \\\\h\\s\\x becomes //h/s/x") {
    assume(isWindows, "UNC nio parsing requires a Windows host")
    assertEquals(FilePathNio.fromNioPath(Paths.get("\\\\h\\s\\x")).pathString, "//h/s/x")
  }

  test("Windows: the UNC model form round-trips through nio") {
    assume(isWindows, "UNC nio parsing requires a Windows host")
    val p = FilePath.of("//h/s/x")
    assertEquals(FilePathNio.fromNioPath(FilePathNio.toNioPath(p)), p)
  }

  test("Windows: toNioPath of the UNC model form parses as an absolute (UNC) nio path") {
    assume(isWindows, "UNC nio parsing requires a Windows host")
    assert(FilePathNio.toNioPath(FilePath.of("//h/s/x")).isAbsolute)
  }

  // ===== POSIX-host assertions =====================================================================

  test("POSIX: the drive form /C:/x is an ordinary relative-looking nio string C:/x") {
    assume(!isWindows, "asserts POSIX nio parsing")
    // On POSIX, "C:" is a legal directory name; the stripped form is a relative path.
    assert(!FilePathNio.toNioPath(FilePath.of("/C:/x")).isAbsolute)
    assertEquals(FilePathNio.toNioPath(FilePath.of("/C:/x")).toString, "C:/x")
  }

  test("POSIX: nio collapses the UNC doubled slash (documented, harmless off Windows)") {
    assume(!isWindows, "asserts POSIX nio parsing")
    assertEquals(FilePathNio.toNioPath(FilePath.of("//h/s/x")).toString, "/h/s/x")
  }

  test("fromNioPath of a directory child matches dir.resolve(name)") {
    // The property list()/walkTree() rely on: children rendered from nio equal dir.resolve(name).
    val dir  = FilePath.of("/base/dir")
    val name = "child.txt"
    val nio  = FilePathNio.toNioPath(dir).resolve(name)
    assertEquals(FilePathNio.fromNioPath(nio), dir.resolve(name))
  }

  // ===== cwd / toAbsolute against the independent nio oracle =======================================
  // (Adapted from ssg FilePathIss980NativeSuite: the host cwd is obtained via raw nio then
  // POSIX-rendered — replace backslash, prefix '/' for a drive-absolute host path — matching the
  // OS-independent model, so these assertions are correct on every host OS.)

  private def independentCwd: String = {
    val host = Paths.get(".").toAbsolutePath.normalize().toString.replace('\\', '/')
    if (host.startsWith("/")) host else "/" + host
  }

  test("cwd returns the real working directory, not the literal dot") {
    val cwd = independentCwd
    assertEquals(FilePath.cwd.pathString, cwd)
    assertNotEquals(FilePath.cwd.pathString, ".")
    assert(FilePath.cwd.isAbsolute)
  }

  test("toAbsolute resolves a relative path against the real working directory") {
    val cwd      = independentCwd
    val absolute = FilePath.of("rel/file.txt").toAbsolute.pathString
    assertEquals(absolute, cwd + "/rel/file.txt")
  }
}
