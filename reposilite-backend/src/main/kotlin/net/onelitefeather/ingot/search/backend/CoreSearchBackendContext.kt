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

@file:OptIn(ExperimentalIngotSearchApi::class)

package net.onelitefeather.ingot.search.backend

import java.nio.file.Files
import java.nio.file.Path
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * The [SearchBackendContext] the core hands to a factory. [backendId] names the backend in every log line, and [sink]
 * receives the lines as `level` and message. [variables] reads the variables; it is the process environment unless a test
 * gives another. Settings are read from the environment on each call, so nothing is cached, but every non-blank value that
 * [secret] hands out is remembered by this instance, so that [redact] can remove it from what the backend shows.
 */
internal class CoreSearchBackendContext(
    override val clock: Clock,
    override val instanceId: String,
    private val backendId: String,
    private val variables: (String) -> String? = { System.getenv(it) },
    private val sink: (level: String, message: String) -> Unit,
) : SearchBackendContext {

    private val handedOut: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun environment(name: String): String? =
        variables("INGOT_$name")?.takeIf { it.isNotBlank() } ?: variables("REPOSILITE_$name")?.takeIf { it.isNotBlank() }

    override fun secret(name: String): String? =
        (variables(name)?.takeIf { it.isNotBlank() }
            ?: variables("${name}_FILE")?.takeIf { it.isNotBlank() }?.let { Files.readString(Path.of(it)).trim() })
            ?.also { if (it.isNotBlank()) handedOut.add(it) }

    /**
     * Removes every value that [secret] handed out from [text], longest first, each occurrence as `***`, and then the user
     * information of every URL, as [redactBackendDetail] does. Empty text and blank values change nothing.
     */
    internal fun redact(text: String): String {
        val withoutSecrets = handedOut.sortedByDescending { it.length }.fold(text) { shown, secret -> shown.replace(secret, "***") }
        return redactBackendDetail(withoutSecrets)
    }

    override fun logger(): BackendLogger = object : BackendLogger {
        override fun info(message: String) = sink("info", line(message))
        override fun warn(message: String) = sink("warn", line(message))
        override fun error(message: String) = sink("error", line(message))
    }

    private fun line(message: String): String = "SEARCH | $backendId | ${redact(message)}"

}
