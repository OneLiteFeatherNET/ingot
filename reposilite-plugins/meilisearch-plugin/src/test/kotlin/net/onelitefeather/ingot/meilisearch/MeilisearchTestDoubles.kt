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

package net.onelitefeather.ingot.meilisearch

import net.onelitefeather.ingot.search.backend.BackendLogger
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.SearchBackendContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A context whose environment is the test's own and whose warnings are recorded. The clock is fixed, so no test reads the system time. */
internal class FakeSearchContext(val environment: Map<String, String> = emptyMap()) : SearchBackendContext {

    val warnings = mutableListOf<String>()

    override val clock: Clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)

    override val instanceId: String = "replica-test"

    override fun environment(name: String): String? = environment["INGOT_$name"] ?: environment["REPOSILITE_$name"]

    override fun secret(name: String): String? = null

    override fun logger(): BackendLogger = object : BackendLogger {
        override fun info(message: String) = Unit
        override fun warn(message: String) {
            warnings.add(message)
        }
        override fun error(message: String) = Unit
    }

}
