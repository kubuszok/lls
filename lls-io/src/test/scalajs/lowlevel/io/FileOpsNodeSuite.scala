/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: adapted from ssg FileOpsIss978JsSuite (Node fs round-trips, existence and type
 * predicates, isSupported), FileOpsStatIss1123JsSuite (single-statSync missing-path predicates),
 * and the Node-oracle cwd/toAbsolute assertions of FilePathIss1127JsSuite /
 * FilePathIss1127ExtraJsSuite (the working directory is obtained independently of the API under
 * test via Node's process module — the suites run under Node, sbt Test/jsEnv is a NodeJSEnv).
 */
package lowlevel
package io

import java.nio.charset.StandardCharsets

import scala.scalajs.js

final class FileOpsNodeSuite extends munit.FunSuite {

  /** Node built-in modules — used only for test setup/teardown and independent oracles, never for the behavior under test.
    */
  private val nodeOs:   js.Dynamic = js.Dynamic.global.require("os")
  private val nodeFs:   js.Dynamic = js.Dynamic.global.require("fs")
  private val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  /** Fresh temp directory for the current test, created in beforeEach via Node (independent of FileOps). */
  private var tempDir: String = ""

  override def beforeEach(context: BeforeEach): Unit = {
    val prefix = nodePath.join(nodeOs.tmpdir(), "lls-io-node-").asInstanceOf[String]
    tempDir = nodeFs.mkdtempSync(prefix).asInstanceOf[String]
  }

  override def afterEach(context: AfterEach): Unit =
    // Recursive cleanup with Node fs; force=true tolerates entries a test never managed to create.
    nodeFs.rmSync(tempDir, js.Dynamic.literal(recursive = true, force = true)): Unit

  /** Joins a child name onto the current temp directory using Node's path module. */
  private def child(name: String): String =
    nodePath.join(tempDir, name).asInstanceOf[String]

  /** Working directory obtained independently of FilePath, via Node's process.cwd(). */
  private def independentCwd: String =
    js.Dynamic.global.require("process").cwd().asInstanceOf[String]

  // ----- fs round-trips against Node-created fixtures ----------------------------------------------

  test("writeBytes then readAllBytes round-trips raw bytes (Node Buffer conversion is byte-exact)") {
    val path  = FilePath.of(child("bytes.bin"))
    val bytes = Array[Byte](0, 1, 2, 127, -128, 42)
    FileOps.writeBytes(path, bytes)
    assertEquals(FileOps.readAllBytes(path).toSeq, bytes.toSeq)
  }

  test("writeString then readString round-trips UTF-8 content") {
    val path    = FilePath.of(child("text.txt"))
    val content = "liquid include: zażółć gęślą jaźń\nsecond line"
    FileOps.writeString(path, content)
    assertEquals(FileOps.readString(path), content)
    assertEquals(FileOps.readString(path, StandardCharsets.UTF_8), content)
  }

  test("exists is true for a Node-created file and false for a missing path") {
    val present = child("present.txt")
    nodeFs.writeFileSync(present, "x"): Unit
    assert(FileOps.exists(FilePath.of(present)))
    assert(!FileOps.exists(FilePath.of(child("missing.txt"))))
  }

  test("isDirectory is true for a directory and false for a regular file") {
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(FileOps.isDirectory(FilePath.of(tempDir)))
    assert(!FileOps.isDirectory(FilePath.of(file)))
  }

  test("isRegularFile is true for a regular file and false for a directory") {
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(FileOps.isRegularFile(FilePath.of(file)))
    assert(!FileOps.isRegularFile(FilePath.of(tempDir)))
  }

  // ----- single-statSync missing-path predicates (the catch path yields false, not an error) -------

  test("isDirectory on a missing path is false, not a raised error") {
    assert(!FileOps.isDirectory(FilePath.of(child("absent-dir"))))
  }

  test("isRegularFile on a missing path is false, not a raised error") {
    assert(!FileOps.isRegularFile(FilePath.of(child("absent-file.txt"))))
  }

  test("isDirectory on a regular file is false") {
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(!FileOps.isDirectory(FilePath.of(file)))
  }

  test("isRegularFile on a directory is false") {
    assert(!FileOps.isRegularFile(FilePath.of(tempDir)))
  }

  // ----- isSupported -------------------------------------------------------------------------------

  test("isSupported reports true under Node (fs is available)") {
    assert(FileOps.isSupported)
  }

  // ----- cwd / toAbsolute against the independent Node oracle --------------------------------------

  test("cwd returns the real working directory, not the literal dot") {
    val cwd = independentCwd
    assertEquals(FilePath.cwd.pathString, cwd)
    assertNotEquals(FilePath.cwd.pathString, ".")
    assert(FilePath.cwd.isAbsolute)
  }

  test("toAbsolute resolves a relative path against the real working directory") {
    val cwd = independentCwd
    assertEquals(FilePath.of("rel/file.txt").toAbsolute.pathString, cwd + "/rel/file.txt")
  }

  test("toAbsolute does not normalize a relative path — a/../b keeps the ..") {
    val cwd = independentCwd
    assertEquals(FilePath.of("a/../b").toAbsolute.pathString, cwd + "/a/../b")
  }
}
