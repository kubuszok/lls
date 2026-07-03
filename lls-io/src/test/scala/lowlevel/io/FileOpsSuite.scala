/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from the ssg suites FileOpsDirectoryIss1121Suite (non-symlink cases; the
 *   symlink cases live in the per-platform FileOpsSymlinkSuite), FileOpsIss977NativeSuite and
 *   FileOpsIss978JsSuite (basic read/write/exists/type predicates — here they run on all three
 *   platforms), and FileOpsStatIss1123JsSuite (missing-path type predicates are false, not
 *   errors — the java.nio.file.Files.isDirectory/isRegularFile contract, uniform per platform).
 *
 * Fixtures live under a unique directory beneath `target/` (cwd-relative; all three test
 * runners execute from the build root). Uniqueness comes from System.nanoTime so
 * parallel/repeat runs do not collide.
 */
package lowlevel
package io

import java.nio.charset.StandardCharsets

final class FileOpsSuite extends munit.FunSuite {

  /** A fresh, unique fixture root beneath target/ for each test, removed afterwards via the API under test. */
  private val root = new munit.Fixture[FilePath]("fileops-root") {
    private var dir: FilePath = FilePath.of("target")

    def apply(): FilePath = dir

    override def beforeEach(context: BeforeEach): Unit = {
      dir = FilePath.of("target").resolve("lls-io-fileops-" + System.nanoTime().toString)
      FileOps.createDirectories(dir)
    }

    override def afterEach(context: AfterEach): Unit =
      FileOps.deleteRecursively(dir)
  }

  override def munitFixtures: Seq[munit.Fixture[?]] = List(root)

  /** Writes `content` to `path`, creating the parent directory tree first so callers can write nested fixtures. */
  private def writeFile(path: FilePath, content: String): Unit = {
    path.parent.foreach(FileOps.createDirectories)
    FileOps.writeString(path, content)
  }

  // ----- read/write round-trips -------------------------------------------------------------------

  test("writeBytes then readAllBytes round-trips raw bytes") {
    val path  = root().resolve("bytes.bin")
    val bytes = Array[Byte](0, 1, 2, 127, -128, 42)
    FileOps.writeBytes(path, bytes)
    assertEquals(FileOps.readAllBytes(path).toSeq, bytes.toSeq)
  }

  test("writeString then readString round-trips UTF-8 content") {
    val path    = root().resolve("text.txt")
    val content = "liquid include: zażółć gęślą jaźń\nsecond line"
    FileOps.writeString(path, content)
    assertEquals(FileOps.readString(path), content)
    assertEquals(FileOps.readString(path, StandardCharsets.UTF_8), content)
  }

  // ----- exists / type predicates -----------------------------------------------------------------

  test("exists is true for an existing file and false for a missing path") {
    val present = root().resolve("present.txt")
    FileOps.writeString(present, "x")
    assert(FileOps.exists(present))
    assert(!FileOps.exists(root().resolve("missing.txt")))
  }

  test("isDirectory is true for a directory and false for a regular file") {
    val file = root().resolve("file.txt")
    FileOps.writeString(file, "x")
    assert(FileOps.isDirectory(root()))
    assert(!FileOps.isDirectory(file))
  }

  test("isRegularFile is true for a regular file and false for a directory") {
    val file = root().resolve("file.txt")
    FileOps.writeString(file, "x")
    assert(FileOps.isRegularFile(file))
    assert(!FileOps.isRegularFile(root()))
  }

  test("isDirectory on a missing path is false, not a raised error") {
    assert(!FileOps.isDirectory(root().resolve("absent-dir")))
  }

  test("isRegularFile on a missing path is false, not a raised error") {
    assert(!FileOps.isRegularFile(root().resolve("absent-file.txt")))
  }

  test("isSupported reports true on every test platform (JVM, Native, JS under Node)") {
    assert(FileOps.isSupported)
  }

  // ----- list -------------------------------------------------------------------------------------

  test("list: returns the immediate children, sorted, full child paths") {
    val dir = root()
    writeFile(dir.resolve("b.txt"), "b")
    writeFile(dir.resolve("a.txt"), "a")
    FileOps.createDirectories(dir.resolve("sub"))
    writeFile(dir.resolve("sub").resolve("deep.txt"), "deep") // must NOT appear: list is one level only

    val children = FileOps.list(dir)
    assertEquals(children.map(_.fileName), List("a.txt", "b.txt", "sub"))
    // Each returned child must be a FULL path under dir, not a bare name: its string starts with dir's string plus a
    // separator and ends with the child's own name. (dir.resolve(name) is exactly how a child path is formed, so the
    // returned path string must equal it.)
    val dirPrefix = dir.pathString
    children.foreach { child =>
      val expectedString = dir.resolve(child.fileName).pathString
      assertEquals(child.pathString, expectedString, s"child must be a full path under dir: ${child.pathString}")
      assert(
        child.pathString.startsWith(dirPrefix) && child.pathString.length > dirPrefix.length + 1,
        s"child path must extend dir's path: ${child.pathString}"
      )
      assert(child.pathString.endsWith(child.fileName), s"child path must end with its own name: ${child.pathString}")
    }
  }

  test("list: deterministic ascending order regardless of creation order") {
    // Ordering is established by explicit sort over the UTF-16 code-unit value of the path string
    // (String.compareTo / sortBy(_.pathString)), NOT by the platform's readdir order. A naive all-lowercase-ASCII
    // fixture would coincide with the macOS APFS readdir UTF-8 order, so dropping the sort would still pass. These
    // discriminator names are chosen so the two orders DISAGREE:
    //   contract (UTF-16 first code unit):  U+4F60 < U+1F600(hi:U+D83D) < U+FF5A < U+FFFD  =>  你 < 😀 < ｚ < �
    //   APFS readdir (UTF-8 byte order):     你 < ｚ < � < 😀
    // They are normalization-stable and avoid same-letter case pairs (APFS case-insensitivity would collapse those).
    val dir       = root()
    val cjk       = "你.txt" // 你
    val grinning  = "😀.txt" // 😀 (non-BMP, surrogate pair)
    val fullwidth = "ｚ.txt" // ｚ
    val replace   = "�.txt" // �
    // Create them in an order matching neither the contract nor readdir, to prove the sort is what fixes the result.
    List(fullwidth, grinning, replace, cjk).foreach(n => writeFile(dir.resolve(n), n))
    assertEquals(FileOps.list(dir).map(_.fileName), List(cjk, grinning, fullwidth, replace))
  }

  test("list: empty directory yields an empty list") {
    val dir = root().resolve("empty")
    FileOps.createDirectories(dir)
    assertEquals(FileOps.list(dir), Nil)
  }

  test("list: a missing path throws") {
    // Contract: behavior on a missing/non-dir path is to throw (mirrors Files.list's missing-path condition).
    val missing = root().resolve("does-not-exist")
    intercept[Throwable](FileOps.list(missing))
  }

  // ----- walkTree ---------------------------------------------------------------------------------

  test("walkTree: recursive pre-order, deterministic, files and dirs, excludes the root itself") {
    val dir = root()
    writeFile(dir.resolve("a.txt"), "a")
    FileOps.createDirectories(dir.resolve("d1"))
    writeFile(dir.resolve("d1").resolve("x.txt"), "x")
    writeFile(dir.resolve("d1").resolve("y.txt"), "y")
    FileOps.createDirectories(dir.resolve("d1").resolve("nested"))
    writeFile(dir.resolve("d1").resolve("nested").resolve("z.txt"), "z")
    writeFile(dir.resolve("b.txt"), "b")

    // Express the expectation relative to the root so it is platform-independent (separator-agnostic via fileName).
    val relative = FileOps.walkTree(dir).map { p =>
      // Build a relative, slash-joined label from the names below `dir`.
      var labels = List(p.fileName)
      var cur    = p.parent
      while (cur.isDefined && cur.get.pathString != dir.pathString) {
        labels = cur.get.fileName :: labels
        cur = cur.get.parent
      }
      labels.mkString("/")
    }
    assertEquals(
      relative,
      List("a.txt", "b.txt", "d1", "d1/nested", "d1/nested/z.txt", "d1/x.txt", "d1/y.txt")
    )
  }

  test("walkTree: a missing path throws") {
    intercept[Throwable](FileOps.walkTree(root().resolve("nope")))
  }

  // ----- createDirectories ------------------------------------------------------------------------

  test("createDirectories: creates a nested tree") {
    val nested = root().resolve("a").resolve("b").resolve("c")
    FileOps.createDirectories(nested)
    assert(FileOps.isDirectory(nested))
    assert(FileOps.isDirectory(root().resolve("a")))
    assert(FileOps.isDirectory(root().resolve("a").resolve("b")))
  }

  test("createDirectories: idempotent on an existing directory") {
    val dir = root().resolve("already")
    FileOps.createDirectories(dir)
    FileOps.createDirectories(dir) // second call must not throw
    assert(FileOps.isDirectory(dir))
  }

  // ----- copy -------------------------------------------------------------------------------------

  test("copy: byte-exact for binary content including bytes >= 0x80") {
    // Byte-exact copy: use bytes with the high bit set to catch any signed/unsigned reinterpretation.
    val dir   = root()
    val src   = dir.resolve("src.bin")
    val dst   = dir.resolve("dst.bin")
    val bytes = Array[Byte](0, 1, 127, -128, -1, -42, 0x80.toByte, 0xff.toByte, 65)
    FileOps.writeBytes(src, bytes)
    FileOps.copy(src, dst)
    assertEquals(FileOps.readAllBytes(dst).toSeq, bytes.toSeq)
  }

  test("copy: replaces an existing destination") {
    val dir = root()
    val src = dir.resolve("new.txt")
    val dst = dir.resolve("old.txt")
    FileOps.writeString(src, "fresh")
    FileOps.writeString(dst, "stale-previous-build-content")
    FileOps.copy(src, dst)
    assertEquals(FileOps.readString(dst), "fresh")
  }

  test("copy: a missing source throws") {
    val dir = root()
    intercept[Throwable](FileOps.copy(dir.resolve("absent.txt"), dir.resolve("out.txt")))
  }

  // ----- lastModifiedTime -------------------------------------------------------------------------

  test("lastModifiedTime: returns a plausible epoch-millis value for an existing file") {
    val dir    = root()
    val file   = dir.resolve("stamp.txt")
    val before = System.currentTimeMillis()
    FileOps.writeString(file, "x")
    val mtime = FileOps.lastModifiedTime(file)
    // A freshly-written file must have a positive mtime that is not implausibly far in the future. Filesystems may
    // truncate to second precision, so allow a generous downward slack rather than asserting mtime >= before exactly.
    assert(mtime > 0L, s"mtime must be positive, was $mtime")
    assert(mtime <= System.currentTimeMillis() + 2000L, s"mtime must not be in the future: $mtime")
    assert(mtime >= before - 5000L, s"mtime must be near write time: $mtime vs before=$before")
  }

  test("lastModifiedTime: a missing path throws") {
    intercept[Throwable](FileOps.lastModifiedTime(root().resolve("no-such-file.txt")))
  }

  // ----- deleteRecursively ------------------------------------------------------------------------

  test("deleteRecursively: removes an entire tree of files and subdirectories") {
    val dir  = root()
    val tree = dir.resolve("tree")
    writeFile(tree.resolve("f.txt"), "f")
    writeFile(tree.resolve("sub").resolve("g.txt"), "g")
    writeFile(tree.resolve("sub").resolve("deeper").resolve("h.txt"), "h")
    assert(FileOps.exists(tree))
    FileOps.deleteRecursively(tree)
    assert(!FileOps.exists(tree))
  }

  test("deleteRecursively: removes a single file") {
    val dir  = root()
    val file = dir.resolve("solo.txt")
    FileOps.writeString(file, "x")
    FileOps.deleteRecursively(file)
    assert(!FileOps.exists(file))
  }

  test("deleteRecursively: a missing path is a no-op") {
    FileOps.deleteRecursively(root().resolve("never-existed"))
    // Reaching here without an exception is the assertion.
    assert(true)
  }
}
