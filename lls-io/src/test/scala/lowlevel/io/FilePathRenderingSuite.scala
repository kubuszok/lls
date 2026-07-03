/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from ssg FilePathIss982Suite (rendering + isAbsolute sections) and
 *   FilePathWin32ParityIss1128Suite (colon/backslash parity), plus the plan §4.1 rows
 *   (///a -> /a, trailing-slash drop, "." / ".." preservation). Runs on all three platforms:
 *   the pure renderer defines the POSIX-string model uniformly.
 */
package lowlevel
package io

final class FilePathRenderingSuite extends munit.FunSuite {

  // ===== §4.1 rendering (of / pathString) ===========================================================

  test("of collapses redundant interior separators — /a//b/ becomes /a/b") {
    assertEquals(FilePath.of("/a//b/").pathString, "/a/b")
  }

  test("of collapses a doubled root separator — // becomes /") {
    assertEquals(FilePath.of("//").pathString, "/")
  }

  test("of collapses a leading run of three-or-more slashes to a single / — ///a becomes /a") {
    assertEquals(FilePath.of("///a").pathString, "/a")
  }

  test("of strips a trailing separator on a relative path — a/b/ becomes a/b") {
    assertEquals(FilePath.of("a/b/").pathString, "a/b")
  }

  test("of strips a trailing separator on an absolute path — /a/b/ becomes /a/b") {
    assertEquals(FilePath.of("/a/b/").pathString, "/a/b")
  }

  test("of keeps the root / as-is") {
    assertEquals(FilePath.of("/").pathString, "/")
  }

  test("of preserves a .. segment — a/../b stays a/../b (not resolved)") {
    assertEquals(FilePath.of("a/../b").pathString, "a/../b")
  }

  test("of preserves a . segment — /a/./b stays /a/./b") {
    assertEquals(FilePath.of("/a/./b").pathString, "/a/./b")
  }

  test("of of the empty string is the empty string") {
    assertEquals(FilePath.of("").pathString, "")
  }

  test("of a simple absolute path preserves it — /usr/local stays /usr/local") {
    assertEquals(FilePath.of("/usr/local").pathString, "/usr/local")
  }

  test("of a simple relative path preserves it — src/main stays src/main") {
    assertEquals(FilePath.of("src/main").pathString, "src/main")
  }

  // ===== §4.1 colon is a legal filename char on POSIX, not a drive-letter indicator ================

  test("of(\"a:b\").isAbsolute is false — colon is not a drive-letter") {
    assert(!FilePath.of("a:b").isAbsolute)
  }

  test("of(\"a:b\").pathString preserves the colon") {
    assertEquals(FilePath.of("a:b").pathString, "a:b")
  }

  test("resolve treats a:b as a relative child, not an absolute replacement") {
    assertEquals(FilePath.of("/x").resolve("a:b").pathString, "/x/a:b")
  }

  // ===== §4.1 addendum: native Windows drive-absolute inputs are lifted into the model form =======
  // (bug-fix-class deviation from ssg, ISS-1383 class: ssg's `of` left "C:/a" / "C:\a" unlifted, so
  // isAbsolute was false for an absolute host path and toAbsolute string-joined cwd onto it. The
  // lift is applied only for <single ASCII letter> ':' <separator>, matching what cwd and
  // fromNioPath already produce; the ISS-1128 colon/backslash-are-filename-chars rows below are
  // unaffected.)

  test("of lifts a native drive-absolute input with forward slashes — C:/a becomes /C:/a") {
    assertEquals(FilePath.of("C:/a").pathString, "/C:/a")
    assert(FilePath.of("C:/a").isAbsolute)
  }

  test("of lifts a native drive-absolute input with backslashes — C:\\a becomes /C:/a") {
    assertEquals(FilePath.of("C:\\a").pathString, "/C:/a")
    assert(FilePath.of("C:\\a").isAbsolute)
  }

  test("of lifts a lower-case drive letter too — c:\\dir\\f.txt becomes /c:/dir/f.txt") {
    assertEquals(FilePath.of("c:\\dir\\f.txt").pathString, "/c:/dir/f.txt")
  }

  test("of lifts a bare drive root — C:/ becomes /C:") {
    assertEquals(FilePath.of("C:/").pathString, "/C:")
    assert(FilePath.of("C:/").isAbsolute)
  }

  test("toAbsolute of a lifted drive-absolute path is itself — cwd is never string-joined onto it") {
    val p = FilePath.of("C:\\dir\\f.txt")
    assertEquals(p.toAbsolute.pathString, "/C:/dir/f.txt")
    assert(
      !p.toAbsolute.pathString.startsWith(FilePath.cwd.pathString + "/"),
      "cwd must not be joined onto an absolute host path"
    )
  }

  test("of does NOT lift a drive-relative input without a separator — C:name stays relative (control)") {
    assertEquals(FilePath.of("C:name").pathString, "C:name")
    assert(!FilePath.of("C:name").isAbsolute)
  }

  // ===== §4.1 backslash is a legal filename char, not a separator ==================================

  test("of(\"a\\\\b\").pathString preserves the backslash — not converted to /") {
    assertEquals(FilePath.of("a\\b").pathString, "a\\b")
  }

  test("of(\"a\\\\b\").isAbsolute is false — backslash does not make a path absolute") {
    assert(!FilePath.of("a\\b").isAbsolute)
  }

  // ===== isAbsolute ================================================================================

  test("an absolute path starting with / reports isAbsolute true") {
    assert(FilePath.of("/usr/local").isAbsolute)
    assert(FilePath.of("/x").isAbsolute)
    assert(FilePath.of("/a/b").isAbsolute)
  }

  test("a relative path reports isAbsolute false") {
    assert(!FilePath.of("src/main").isAbsolute)
    assert(!FilePath.of("a/b").isAbsolute)
  }

  test("the root / is absolute") {
    assert(FilePath.of("/").isAbsolute)
  }

  test("the empty string is not absolute") {
    assert(!FilePath.of("").isAbsolute)
  }

  // ===== control cases ============================================================================

  test("of(\"/a/b\").pathString is unchanged — control") {
    assertEquals(FilePath.of("/a/b").pathString, "/a/b")
  }

  test("of(\"a/b\").pathString is unchanged — control") {
    assertEquals(FilePath.of("a/b").pathString, "a/b")
  }

  // ===== object constants =========================================================================

  test("separatorChar is always '/'") {
    assertEquals(FilePath.separatorChar, '/')
  }

  test("toString equals pathString") {
    assertEquals(FilePath.of("/a/b/c").toString, "/a/b/c")
  }

  test("equal path strings are equal and hash equally") {
    assertEquals(FilePath.of("/a/b"), FilePath.of("/a//b/"))
    assertEquals(FilePath.of("/a/b").hashCode, FilePath.of("/a//b/").hashCode)
  }
}
