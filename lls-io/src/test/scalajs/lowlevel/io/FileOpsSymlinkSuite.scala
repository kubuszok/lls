/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from the symlink cases of ssg FileOpsDirectoryIss1121Suite (walkTree
 * no-follow, deleteRecursively containment; ssg ISS-1121/1347). The suite text is identical in
 * every platform test dir; only SymlinkTestSupport differs per platform.
 */
package lowlevel
package io

final class FileOpsSymlinkSuite extends munit.FunSuite {

  /** A fresh, unique fixture root beneath target/ for each test, removed afterwards via the API under test. */
  private val root = new munit.Fixture[FilePath]("fileops-symlink-root") {
    private var dir: FilePath = FilePath.of("target")

    def apply(): FilePath = dir

    override def beforeEach(context: BeforeEach): Unit = {
      dir = FilePath.of("target").resolve("lls-io-symlink-" + System.nanoTime().toString)
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

  test("walkTree: does not follow directory symlinks (returns the link entry, never its target's contents)") {
    // Contract: a directory symlink is returned as an entry but its target's contents are not descended into; this
    // both bounds the traversal to the subtree under `path` and guards against cycles (a link pointing back into the
    // tree would otherwise recurse forever). Guarded by SymlinkTestSupport so a host without symlink capability
    // degrades to a still-meaningful structural assertion.
    val dir = root()

    // A target directory holding contents that must NOT be enumerated through the link.
    val target      = dir.resolve("target")
    val targetChild = target.resolve("inner.txt")
    writeFile(targetChild, "behind-the-link")
    FileOps.createDirectories(target.resolve("subdir"))

    // The walked tree: one real file plus (conditionally) a directory symlink that points at `target`.
    val tree = dir.resolve("tree")
    FileOps.createDirectories(tree)
    writeFile(tree.resolve("real.txt"), "real")
    val link = tree.resolve("link")

    // Probe whether both symlink creation AND NOFOLLOW_LINKS are honored BEFORE creating the test's own directory
    // symlink. On Native-Windows (symlinks work but NOFOLLOW is not honored, ssg ISS-1347), skipping symlink creation
    // prevents the fixture teardown's deleteRecursively from recursing through the link and throwing.
    if (SymlinkTestSupport.symlinkSafelyTestable(dir, target)) {
      // Both symlink creation and NOFOLLOW are supported (JVM, JS, macOS/linux-Native): create the symlink and run
      // the full does-not-follow security assertions.
      val created = SymlinkTestSupport.tryCreateSymlink(link, target)
      assert(created, "symlinkSafelyTestable returned true but tryCreateSymlink failed")

      // The link must resolve (live, not dangling) — otherwise "did not descend" would be vacuously true.
      assert(FileOps.isDirectory(link), "the directory symlink must resolve to its target before walking")
      assert(FileOps.exists(link.resolve("inner.txt")), "following the link must reach the target's contents")

      val names = FileOps.walkTree(tree).map(_.fileName).toSet
      // The link entry itself IS returned.
      assert(names.contains("link"), "walkTree must return the symlink entry itself")
      assert(names.contains("real.txt"), "walkTree must return the real file in the tree")
      // NOTHING from beneath the target is enumerated through the link: not the target's file, not its subdir.
      assert(!names.contains("inner.txt"), "walkTree must NOT enumerate the target's file through the link")
      assert(!names.contains("subdir"), "walkTree must NOT enumerate the target's subdirectory through the link")
      // Exactly the two in-tree entries — no descent past the link of any kind.
      assertEquals(FileOps.walkTree(tree).map(_.fileName).sorted, List("link", "real.txt"))
    } else {
      // This platform either cannot create symlinks or does not honor NOFOLLOW_LINKS (Native-Windows, ssg ISS-1347).
      // No directory symlink is created — the degraded assertion confirms the tree walk works on the real file alone,
      // and the fixture teardown's deleteRecursively never encounters a symlink it would recurse through.
      assertEquals(FileOps.walkTree(tree).map(_.fileName), List("real.txt"))
    }
  }

  test("deleteRecursively: does not follow a directory symlink (clean-build safety)") {
    // Contract: a symlink is deleted as a link; the target and its contents survive. Guarded by a platform-capability
    // check (SymlinkTestSupport per platform) rather than munit assume, so the structure stays readable: on a host
    // that cannot create symlinks the degraded branch still exercises deleteRecursively without a symlink.
    val dir = root()

    // An "outside" directory that must survive: it holds a file the symlink points at.
    val outside       = dir.resolve("outside")
    val protectedFile = outside.resolve("keep.txt")
    writeFile(protectedFile, "must-survive")

    // The build directory to be cleaned; it will contain a symlink to `outside` only when safe.
    val buildDir = dir.resolve("build")
    FileOps.createDirectories(buildDir)
    val link = buildDir.resolve("link-to-outside")

    // Probe whether both symlink creation AND NOFOLLOW_LINKS are honored BEFORE creating the test's own directory
    // symlink (ssg ISS-1347, as above).
    if (SymlinkTestSupport.symlinkSafelyTestable(dir, outside)) {
      // Both symlink creation and NOFOLLOW are supported (JVM, JS, macOS/linux-Native): create the symlink and run
      // the full does-not-follow security assertions.
      val created = SymlinkTestSupport.tryCreateSymlink(link, outside)
      assert(created, "symlinkSafelyTestable returned true but tryCreateSymlink failed")

      // The link is an entry under buildDir before the clean (proving this branch actually ran on this platform).
      assertEquals(FileOps.list(buildDir).map(_.fileName), List("link-to-outside"))
      // Pre-clean guard: the link must RESOLVE to its target directory. isDirectory follows links, so a true result
      // proves the link is live (not dangling) and points at the real `outside` directory. Without this guard a
      // dangling link could let a follow-semantics deletion pass vacuously — it would simply never reach `outside`.
      assert(FileOps.isDirectory(link), "the symlink must resolve to its target directory before the clean")
      assert(
        FileOps.exists(link.resolve("keep.txt")),
        "following the symlink must reach the protected file before the clean (proves the link is not dangling)"
      )
      FileOps.deleteRecursively(buildDir)
      assert(!FileOps.exists(buildDir), "the build directory (and the link inside it) must be gone")
      // NOFOLLOW_LINKS is honored: the target and its contents must survive the clean because deleteRecursively
      // removed the link as a link, never descending into the target.
      assert(FileOps.exists(outside), "the symlink target directory must NOT be deleted")
      assert(FileOps.exists(protectedFile), "the file behind the symlink must NOT be deleted")
      assertEquals(FileOps.readString(protectedFile), "must-survive")
    } else {
      // This platform either cannot create symlinks or does not honor NOFOLLOW_LINKS (Native-Windows, ssg ISS-1347).
      // No directory symlink is created — the degraded assertion confirms deleteRecursively works on the build
      // directory without a symlink, and the fixture teardown never encounters a symlink it would recurse through.
      // The protected file trivially still exists since nothing links to it.
      FileOps.deleteRecursively(buildDir)
      assert(!FileOps.exists(buildDir), "the build directory must be gone")
      assert(FileOps.exists(protectedFile))
    }
  }
}
