/*
 * Copyright (c) 2026 Mateusz Kubuszok
 * SPDX-License-Identifier: Apache-2.0
 *
 * Provenance: cwd bridge extracted from ssg
 *   ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala (cwd).
 * Identical body to the scalajvm variant (plan §5).
 */
package lowlevel
package io

import java.nio.file.Paths

private[io] object FilePathCompat {

  /** Host cwd rendered POSIX-style; on Windows the drive-absolute host path is prefixed with '/' so FilePath.cwd.isAbsolute holds on every OS (no-op on POSIX).
    */
  def cwdString: String = {
    val host = Paths.get(".").toAbsolutePath.normalize().toString.replace('\\', '/')
    if (host.startsWith("/")) host else "/" + host
  }
}
