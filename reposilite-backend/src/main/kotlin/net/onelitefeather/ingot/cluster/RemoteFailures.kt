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

package net.onelitefeather.ingot.cluster

import com.reposilite.status.FailureRecord

/**
 * The failures other instances recorded, kept in memory and merged in on read. Each distinct failure of an origin is one
 * record whose count grows with every message. The store is bounded: the least recently reported failure is dropped first.
 * A stack trace never leaves the instance that recorded it, so these records carry an empty one.
 */
internal class RemoteFailures(private val capacity: Int = DEFAULT_CAPACITY) {

    private val lock = Any()
    private val records = LinkedHashMap<String, FailureRecord>()
    private var lastId = 0L

    /** Applies a `failure.recorded` message. A message without a source or a type is ignored. */
    fun accept(envelope: ClusterEnvelope) {
        val source = envelope.payload.path("source").takeIf { it.isTextual }?.asText() ?: return
        val type = envelope.payload.path("type").takeIf { it.isTextual }?.asText() ?: return
        val message = envelope.payload.path("message").takeIf { it.isTextual }?.asText()
        val key = listOf(envelope.origin, source, type, message).joinToString(separator = "\u0000")

        synchronized(lock) {
            val existing = records.remove(key)
            records[key] = existing?.copy(timestamp = envelope.at, count = existing.count + 1)
                ?: FailureRecord(
                    id = --lastId,
                    timestamp = envelope.at,
                    source = source,
                    type = type,
                    message = message,
                    stacktrace = "",
                    count = 1,
                )
            while (records.size > capacity) {
                records.remove(records.keys.first())
            }
        }
    }

    fun snapshot(): List<FailureRecord> =
        synchronized(lock) { records.values.toList() }

    private companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
