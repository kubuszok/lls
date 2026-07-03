/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: new suite pinning the plan §4.4 UNC table (every row a test case) and the
 * §4.5 drive/UNC normalize floors. No ssg predecessor — UNC handling is new-origin behavior
 * (ssg's renderPath collapsed the leading "//", the latent data-loss bug this module fixes).
 */
package lowlevel
package io

final class FilePathUncSuite extends munit.FunSuite {

  // ===== §4.4 `of` rendering ========================================================================

  test("of preserves a leading // followed by a non-slash as the UNC marker") {
    assertEquals(FilePath.of("//server/share/x").pathString, "//server/share/x")
  }

  test("of collapses interior duplicate separators but keeps the UNC marker") {
    assertEquals(FilePath.of("//h//s///x").pathString, "//h/s/x")
  }

  test("of collapses a leading run of three-or-more slashes to a single /") {
    assertEquals(FilePath.of("///a").pathString, "/a")
  }

  test("of of // alone renders as the root /") {
    assertEquals(FilePath.of("//").pathString, "/")
  }

  test("of drops a trailing separator on a UNC path") {
    assertEquals(FilePath.of("//h/s/x/").pathString, "//h/s/x")
  }

  // ===== §4.1 backslash is a legal filename char (never a separator) ================================

  test("of preserves a backslash as an ordinary filename character") {
    assertEquals(FilePath.of("a\\b").pathString, "a\\b")
  }

  // ===== §4.4 isAbsolute ============================================================================

  test("a UNC path is absolute (leading /)") {
    assert(FilePath.of("//h/s").isAbsolute)
    assert(FilePath.of("//h").isAbsolute)
  }

  // ===== §4.4 parent floors =========================================================================

  test("parent of //h/s/x is //h/s") {
    assertEquals(FilePath.of("//h/s/x").parent.map(_.pathString), Some("//h/s"))
  }

  test("parent of the UNC root //h/s is None") {
    assertEquals(FilePath.of("//h/s").parent, None)
  }

  test("parent of the single-segment UNC root //h is None") {
    assertEquals(FilePath.of("//h").parent, None)
  }

  // ===== §4.4 fileName floors =======================================================================

  test("fileName of //h/s/x is x") {
    assertEquals(FilePath.of("//h/s/x").fileName, "x")
  }

  test("fileName of the UNC root //h/s is the empty string") {
    assertEquals(FilePath.of("//h/s").fileName, "")
  }

  test("fileName of the single-segment UNC root //h is the empty string") {
    assertEquals(FilePath.of("//h").fileName, "")
  }

  // ===== §4.4 / §4.5 normalize floors ===============================================================

  test("normalize: .. never rises above a two-segment UNC root — //h/s/a/../.. is //h/s") {
    assertEquals(FilePath.of("//h/s/a/../..").normalize.pathString, "//h/s")
  }

  test("normalize: .. at a two-segment UNC root is floored — //h/s/.. is //h/s") {
    assertEquals(FilePath.of("//h/s/..").normalize.pathString, "//h/s")
  }

  test("normalize: .. at a single-segment UNC root is floored — //h/.. is //h") {
    assertEquals(FilePath.of("//h/..").normalize.pathString, "//h")
  }

  test("normalize: the // prefix is never lost when .. reduces below the share — //h/a/../.. is //h/a") {
    // PLAN DEVIATION (reported): plan §4.4 pins this at "//h", but that is inconsistent with the plan's
    // own root rule ("//host/share when >=2 segments exist" makes "//h/a" a two-segment UNC root) and
    // with the pinned "//h/s/.." == "//h/s": no deterministic string algorithm can keep the share in
    // "//h/s/.." yet drop it in "//h/a/../..". This module uses the consistent value "//h/a" (share is a
    // floor, matching parent/fileName), preserving the invariant the plan cares about: the "//" marker
    // survives and ".." never escapes the UNC root.
    assertEquals(FilePath.of("//h/a/../..").normalize.pathString, "//h/a")
  }

  test("normalize: a UNC path with no dot segments is unchanged") {
    assertEquals(FilePath.of("//h/s/x").normalize.pathString, "//h/s/x")
  }

  // ===== §4.4 resolve ===============================================================================

  test("resolve of a relative child appends under the UNC path") {
    assertEquals(FilePath.of("//h/s").resolve("sub").pathString, "//h/s/sub")
  }

  test("resolve of a plain absolute child replaces the UNC base") {
    assertEquals(FilePath.of("//h/s/x").resolve("/other").pathString, "/other")
  }

  test("resolve of a UNC child replaces the UNC base, keeping the marker") {
    assertEquals(FilePath.of("//h/s").resolve("//new/share").pathString, "//new/share")
  }

  test("resolve renders the absolute child — resolve(\"//h//s\") keeps the UNC marker as //h/s") {
    assertEquals(FilePath.of("/base").resolve("//h//s").pathString, "//h/s")
  }

  // ===== §4.4 equality ==============================================================================

  test("equality: /a and //a are distinct (distinct strings, distinct meanings)") {
    assertNotEquals(FilePath.of("/a"), FilePath.of("//a"))
    assertNotEquals(FilePath.of("/a").pathString, FilePath.of("//a").pathString)
  }

  // ===== §4.5 drive-root normalize floor ============================================================

  test("normalize: the drive segment is a floor — /C:/a/../.. is /C:") {
    assertEquals(FilePath.of("/C:/a/../..").normalize.pathString, "/C:")
  }

  test("normalize: .. at the drive root is floored — /C:/.. is /C:") {
    assertEquals(FilePath.of("/C:/..").normalize.pathString, "/C:")
  }

  test("normalize: a plain absolute path keeps POSIX behavior — /a/../../.. is /") {
    assertEquals(FilePath.of("/a/../../..").normalize.pathString, "/")
  }
}
