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
package net.onelitefeather.ingot.jobs

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64

/** The opaque cursor of the Jobs page: base64url of `<start in epoch milliseconds>|<source>|<local id>`. */
object JobCursorCodec {

    fun encode(cursor: JobCursor): String {
        val text = "${cursor.startedAt.toEpochMilli()}|${cursor.source}|${cursor.localId}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(StandardCharsets.UTF_8))
    }

    fun decode(value: String): JobCursor {
        val text = try {
            String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid job cursor", exception)
        }
        val parts = text.split('|', limit = 3)
        val millis = parts.getOrNull(0)?.toLongOrNull()
        require(millis != null && parts.size == 3) { "Invalid job cursor" }
        return JobCursor(Instant.ofEpochMilli(millis), parts[1], parts[2])
    }

}
