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

package net.onelitefeather.ingot.cleanup

import com.reposilite.plugin.api.Facade
import com.reposilite.scheduling.ScheduledJobClaims
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import java.time.Clock
import java.time.Instant

/**
 * The two things the cleanup plugin needs from the core and nothing else: the mirror copies of a repository and a claim
 * store shared by every instance. It exists for the cleanup plugin and may change with it.
 */
@ExperimentalIngotPluginApi
interface CleanupSupportFacade : Facade {

    /** The version directories that hold mirror copies of [repository], with their last access. */
    fun mirrorCacheEntries(repository: String): Map<String, Instant>

    /** A claim store shared by every instance through the server's database, which reads the time from [clock]. */
    fun scheduledJobClaims(clock: Clock): ScheduledJobClaims

}
