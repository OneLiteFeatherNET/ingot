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

package net.onelitefeather.ingot.readonly

import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * The read-only state of one instance, reused for at most [maxAge] before the store is read again. A forced state is answered
 * without reading the store. [refresh] re-reads at once, which is what a received change announcement asks for.
 */
class ReadOnlyStateCache(
    private val clock: Clock,
    private val store: ReadOnlyStore,
    private val setting: ReadOnlySetting,
    private val maxAge: Duration = DEFAULT_MAX_AGE,
) {

    private var storedEnabled = false
    private var loadedAt: Instant? = null

    @Synchronized
    fun current(): ReadOnlyState {
        if (setting.forced) return ReadOnlyState.resolve(setting, storedEnabled = false)
        val loaded = loadedAt
        if (loaded == null || Duration.between(loaded, clock.instant()) >= maxAge) {
            reload()
        }
        return ReadOnlyState.resolve(setting, storedEnabled)
    }

    @Synchronized
    fun refresh() {
        reload()
    }

    private fun reload() {
        storedEnabled = store.read()?.enabled ?: false
        loadedAt = clock.instant()
    }

    companion object {

        /** The longest a replica may answer with a state it read before. A lost change announcement delays a change by this much. */
        val DEFAULT_MAX_AGE: Duration = Duration.ofSeconds(5)
    }
}
