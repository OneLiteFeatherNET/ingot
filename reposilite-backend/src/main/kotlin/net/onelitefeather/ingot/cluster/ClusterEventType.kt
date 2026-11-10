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

/**
 * The event types of envelope version 1. [payloadFields] lists every field a payload of the type may carry. None of them
 * is secret material: names, identifiers and figures only.
 */
enum class ClusterEventType(val wireName: String, val payloadFields: Set<String>) {

    RESOLUTION_INVALIDATED("resolution.invalidated", setOf("repository", "path")),
    TOKEN_CHANGED("token.changed", setOf("name")),
    SESSION_REVOKED("session.revoked", setOf("family")),
    CONFIG_CHANGED("config.changed", setOf("domain")),
    JAVADOC_INVALIDATED("javadoc.invalidated", setOf("repository", "path")),
    STORAGE_USAGE("storage.usage", setOf("computedAt", "repositories")),
    CONSOLE_LINE("console.line", setOf("channel", "message", "timestamp")),
    FAILURE_RECORDED("failure.recorded", setOf("source", "type", "message")),
    STATUS_SNAPSHOT(
        "status.snapshot",
        setOf("version", "uptimeStart", "usedMemory", "maxMemory", "usedThreads", "maxThreads", "failuresCount", "health", "interval", "sample"),
    ),
    READ_ONLY_CHANGED("read-only.changed", setOf("enabled")),
    ;

    companion object {

        fun fromWireName(wireName: String): ClusterEventType? =
            entries.firstOrNull { it.wireName == wireName }
    }
}
