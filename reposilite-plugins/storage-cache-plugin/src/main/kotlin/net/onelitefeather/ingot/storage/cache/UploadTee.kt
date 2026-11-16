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

package net.onelitefeather.ingot.storage.cache

import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Copies every byte that the delegate reads from an upload into [output]. A failing copy stops copying, reports the failure and lets the
 * upload go on, so the cache never fails a write. [finish] closes the copy and tells whether it holds the whole upload.
 */
internal class UploadTee(
    input: InputStream,
    private var output: OutputStream?,
    private val report: (String) -> Unit,
) : FilterInputStream(input) {

    /** The number of bytes that reached the copy. */
    var copied: Long = 0L
        private set

    private var reachedEnd = false

    override fun read(): Int {
        val value = super.read()
        if (value < 0) {
            reachedEnd = true
        } else {
            copy(byteArrayOf(value.toByte()), 0, 1)
        }
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val count = super.read(buffer, offset, length)
        if (count < 0) {
            reachedEnd = true
        } else if (count > 0) {
            copy(buffer, offset, count)
        }
        return count
    }

    private fun copy(buffer: ByteArray, offset: Int, length: Int) {
        val target = output ?: return
        try {
            target.write(buffer, offset, length)
            copied += length
        } catch (exception: IOException) {
            report("Cannot copy the upload into the cache, the upload continues without a copy: ${exception.message}")
            output = null
            runCatching { target.close() }
        }
    }

    /** Closes the copy and returns true only when the delegate read the whole upload and no byte was lost on the way. */
    fun finish(): Boolean {
        val target = output
        output = null
        runCatching { target?.close() }
        return reachedEnd && target != null
    }

}
