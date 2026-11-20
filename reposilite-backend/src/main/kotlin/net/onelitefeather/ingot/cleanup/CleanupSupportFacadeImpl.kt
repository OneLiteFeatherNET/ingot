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

import com.reposilite.maven.MavenFacade
import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock
import java.time.Instant

@ExperimentalIngotPluginApi
internal class CleanupSupportFacadeImpl(
    private val maven: MavenFacade,
    private val database: Database,
) : CleanupSupportFacade {

    override fun mirrorCacheEntries(repository: String): Map<String, Instant> =
        maven.mirrorCacheEntries(repository)

    override fun scheduledJobClaims(clock: Clock): ScheduledJobClaims =
        SqlScheduledJobClaims(database, clock)

}
