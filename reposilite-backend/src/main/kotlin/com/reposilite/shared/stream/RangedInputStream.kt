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

package com.reposilite.shared.stream

import java.io.FilterInputStream
import java.io.InputStream

/**
 * The window of [length] bytes that starts at [start] in [source]. The bytes before the window are skipped without
 * being buffered (a file stream seeks), and the stream ends after the window even when the source goes on.
 */
class RangedInputStream(source: InputStream, start: Long, length: Long) : FilterInputStream(source) {

    private var skipping = start
    private var remaining = length

    private fun seek() {
        while (skipping > 0) {
            val skipped = `in`.skip(skipping)
            when {
                skipped > 0 -> skipping -= skipped
                `in`.read() < 0 -> {
                    skipping = 0
                    remaining = 0
                }
                else -> skipping--
            }
        }
    }

    override fun read(): Int {
        seek()
        if (remaining <= 0) return -1
        val value = super.read()
        if (value >= 0) remaining--
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        seek()
        if (len == 0) return 0
        if (remaining <= 0) return -1
        val count = super.read(b, off, minOf(len.toLong(), remaining).toInt())
        if (count > 0) remaining -= count
        return count
    }

    override fun skip(n: Long): Long {
        seek()
        val skipped = super.skip(minOf(n, remaining).coerceAtLeast(0))
        remaining -= skipped
        return skipped
    }

    override fun available(): Int = minOf(super.available().toLong(), remaining).toInt()

    override fun markSupported(): Boolean = false

}
