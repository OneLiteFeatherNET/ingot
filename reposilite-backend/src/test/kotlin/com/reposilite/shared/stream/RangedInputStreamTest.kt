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

@file:Suppress("FunctionName")

package com.reposilite.shared.stream

import java.io.ByteArrayInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RangedInputStreamTest {

    private val data = ByteArray(100) { it.toByte() }

    @Test
    fun `should read only the requested window`() {
        val bytes = RangedInputStream(data.inputStream(), start = 10, length = 5).use { it.readBytes() }

        assertThat(bytes).isEqualTo(data.copyOfRange(10, 15))
    }

    @Test
    fun `should read single bytes up to the end of the window`() {
        val stream = RangedInputStream(data.inputStream(), start = 98, length = 2)

        assertThat(listOf(stream.read(), stream.read(), stream.read())).containsExactly(98, 99, -1)
    }

    @Test
    fun `should end early when the source is shorter than the window`() {
        val bytes = RangedInputStream(data.inputStream(), start = 95, length = 50).use { it.readBytes() }

        assertThat(bytes).isEqualTo(data.copyOfRange(95, 100))
    }

    @Test
    fun `should close the source`() {
        var closed = false
        val source = object : ByteArrayInputStream(data) {
            override fun close() {
                closed = true
            }
        }

        RangedInputStream(source, start = 0, length = 1).close()

        assertThat(closed).isTrue
    }

}
