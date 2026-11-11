/*
 * Copyright (c) 2026 OneLiteFeather
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.onelitefeather.ingot.support

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The bundle is built in memory and refused as a whole when it is larger than the cap. No part of it is ever served. */
class BundleTooLargeException(limit: Long) : RuntimeException("The support bundle exceeds the limit of $limit bytes")

/** Writes the files of a bundle into one zip archive. */
object SupportBundleWriter {

    /** The size cap of one bundle, in bytes. */
    const val MAX_BYTES: Long = 8L * 1024 * 1024

    /**
     * @throws BundleTooLargeException when the archive would exceed [maxBytes]
     */
    fun write(files: Map<String, ByteArray>, maxBytes: Long = MAX_BYTES): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            files.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(content)
                zip.closeEntry()
            }
        }

        val archive = buffer.toByteArray()
        if (archive.size > maxBytes) throw BundleTooLargeException(maxBytes)
        return archive
    }
}
