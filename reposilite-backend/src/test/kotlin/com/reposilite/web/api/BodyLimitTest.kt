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

package com.reposilite.web.api

import io.javalin.http.HttpStatus.PAYLOAD_TOO_LARGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

internal class BodyLimitTest {

    /** An endless body of 'a' that counts how many bytes were pulled from it. */
    private class EndlessBody : InputStream() {
        var bytesRead = 0L
            private set

        override fun read(): Int {
            bytesRead++
            return 'a'.code
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            bytesRead += length
            buffer.fill('a'.code.toByte(), offset, offset + length)
            return length
        }
    }

    /** A body that must never be read, because the declared length already refuses it. */
    private object UnreadableBody : InputStream() {
        override fun read(): Int = throw AssertionError("the body must not be read when the declared length is too large")
    }

    @Test
    fun `caps small JSON bodies at 64 KiB`() {
        assertThat(SMALL_JSON_BODY_LIMIT).isEqualTo(64 * 1024L)
    }

    @Test
    fun `answers 413 without reading the body when the declared length exceeds the limit`() {
        val result = readBoundedBody(declaredLength = 100, input = UnreadableBody, limit = 64)

        assertThat(result.isError).isTrue()
        assertThat(result.error.status).isEqualTo(PAYLOAD_TOO_LARGE.code)
    }

    @Test
    fun `accepts a body of exactly the limit`() {
        val body = ByteArray(64) { 'x'.code.toByte() }

        val result = readBoundedBody(declaredLength = 64, input = ByteArrayInputStream(body), limit = 64)

        assertThat(result.isOk).describedAs("a body of exactly the limit is still served").isTrue()
        assertThat(result.get()).isEqualTo(body)
    }

    @Test
    fun `answers 413 for a chunked body above the limit and reads no more than the limit plus one byte`() {
        val body = EndlessBody()

        val result = readBoundedBody(declaredLength = -1, input = body, limit = 64)

        assertThat(result.isError).isTrue()
        assertThat(result.error.status).isEqualTo(PAYLOAD_TOO_LARGE.code)
        assertThat(body.bytesRead).describedAs("the reader must stop at the limit plus one byte").isLessThanOrEqualTo(65L)
    }

    @Test
    fun `answers 413 for a body above the limit even when the declared length is small`() {
        val result = readBoundedBody(declaredLength = 10, input = ByteArrayInputStream(ByteArray(100)), limit = 64)

        assertThat(result.isError).describedAs("the declared length must not be trusted over the bytes received").isTrue()
        assertThat(result.error.status).isEqualTo(PAYLOAD_TOO_LARGE.code)
    }

    @Test
    fun `answers 413 with a message that names the limit`() {
        val result = readBoundedBody(declaredLength = SMALL_JSON_BODY_LIMIT + 1, input = UnreadableBody, limit = SMALL_JSON_BODY_LIMIT)

        assertThat(result.error.message).describedAs("the client must learn why the request was refused").contains("64 KiB")
    }

}
