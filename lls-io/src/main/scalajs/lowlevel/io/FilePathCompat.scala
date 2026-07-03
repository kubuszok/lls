/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: cwd bridge extracted from ssg
 *   ssg-commons/src/main/scalajs/ssg/commons/io/FilePathPlatform.scala (cwd, process.cwd()).
 */
package lowlevel
package io

import scala.scalajs.js

private[io] object FilePathCompat {

  /** Node's `process`, acquired lazily so a browser bundle (no `require`) does not crash at module init; it fails only when the host cwd is actually requested.
    */
  private lazy val process: js.Dynamic =
    js.Dynamic.global.require("process")

  def cwdString: String =
    process.cwd().asInstanceOf[String]
}
