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

import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.toErrorResult
import io.javalin.http.HttpStatus.CONTENT_TOO_LARGE
import panda.std.Result
import panda.std.asSuccess
import java.io.InputStream

/** The largest body of the small JSON endpoints, such as logins, setup, tokens and identity records. */
const val SMALL_JSON_BODY_LIMIT: Long = 64 * 1024L

/**
 * Reads the body of a request of at most [limit] bytes. A declared length above the limit is refused before a byte is
 * read. Otherwise at most [limit] + 1 bytes are read, so a chunked body, a missing length or a length that lies is
 * refused without being buffered in full.
 *
 * @param declaredLength the Content-Length of the request, or -1 when the request does not declare one
 */
fun readBoundedBody(declaredLength: Long, input: InputStream, limit: Long): Result<ByteArray, ErrorResponse> {
    if (declaredLength > limit) {
        return tooLarge(limit)
    }

    val bytes = input.use { it.readNBytes((limit + 1).toInt()) }
    return if (bytes.size > limit) tooLarge(limit) else bytes.asSuccess()
}

private fun tooLarge(limit: Long): Result<ByteArray, ErrorResponse> =
    CONTENT_TOO_LARGE.toErrorResult("The request body is larger than ${limit / 1024} KiB")
