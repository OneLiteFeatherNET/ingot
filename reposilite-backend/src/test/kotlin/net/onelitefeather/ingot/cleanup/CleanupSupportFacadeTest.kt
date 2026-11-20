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

import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.scheduling.SqliteTestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset.UTC

/**
 * The core side of the narrow interface the cleanup plugin reaches the core through (extract-cleanup-plugin, design D14):
 * the mirror copies of a repository and a claim store on the server's database.
 */
internal class CleanupSupportFacadeTest : MavenSpecification() {

    private val mirror = "mirror"
    private val hosted = "hosted"
    private val versionDirectory = "com/example/app/1.0.0"
    private val lastAccess = Instant.parse("2026-11-01T10:15:30Z")
    private val clock = Clock.fixed(Instant.parse("2026-11-20T03:00:00Z"), UTC)
    private val sqlite = SqliteTestDatabase()

    override fun repositories() = listOf(
        RepositorySettings(hosted, visibility = PUBLIC),
        RepositorySettings(
            mirror,
            visibility = PUBLIC,
            proxied = mutableListOf(MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true, authorization = REMOTE_AUTH)),
        ),
    )

    private fun support(): CleanupSupportFacade =
        CleanupSupportFacadeImpl(mavenFacade, sqlite.database)

    @AfterEach
    fun closeDatabase() = sqlite.close()

    @Test
    fun `should return the mirror copies the maven facade records for a repository`() {
        mirrorCacheLedger.insertIfAbsent(mirror, versionDirectory, lastAccess, sourceHost = null)

        assertThat(support().mirrorCacheEntries(mirror))
            .`as`("mirror copies of the mirror repository, with their last access")
            .containsExactlyEntriesOf(mapOf(versionDirectory to lastAccess))
    }

    @Test
    fun `should return no entries for a repository without mirror copies`() {
        mirrorCacheLedger.insertIfAbsent(mirror, versionDirectory, lastAccess, sourceHost = null)

        assertThat(support().mirrorCacheEntries(hosted))
            .`as`("mirror copies of a repository that holds none")
            .isEmpty()
    }

    @Test
    fun `should grant a key to one of two claim stores on the same database`() {
        val support = support()
        val first = support.scheduledJobClaims(clock)
        val second = support.scheduledJobClaims(clock)
        val key = "cleanup:nightly:2026-11-20"

        assertThat(first.claim(key)).`as`("claim of the first store").isTrue()
        assertThat(second.claim(key)).`as`("claim of the same key by the second store").isFalse()
    }

}
