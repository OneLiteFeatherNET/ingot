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

package com.reposilite.cleanup

import com.reposilite.cleanup.application.CleanupPolicy
import com.reposilite.cleanup.application.CleanupScope
import com.reposilite.cleanup.infrastructure.InMemoryCleanupRunRepository
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.token.AccessTokenIdentifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.io.path.createDirectories

/**
 * Two cleanup services stand for two replicas that share one database. The first service is still live, inside its
 * deletion loop, when the second one is asked to run the same policy by hand. The call is made from the deleter, so no
 * thread or wait is needed.
 */
internal class CleanupRunExclusivityTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val actor = CleanupActor(by = "manager", accessToken = AccessTokenIdentifier())
    private val policy = CleanupPolicy(
        id = "keep-one",
        enabled = true,
        repositories = listOf("releases"),
        scope = CleanupScope.RELEASES,
        keepLatest = 1,
    )

    @Test
    fun `refuses a manual run on a second instance while the first run of the policy is live`() {
        // given: two versions of one artifact, a policy that keeps the newest, and two services over the same policy
        val storage = storage()
        storage.writeVersion("net/example/app", "1.0")
        storage.writeVersion("net/example/app", "2.0")
        val calls = mutableListOf<String>()
        var secondRun: Result<CleanupRun, ErrorResponse>? = null
        lateinit var second: CleanupService

        val first = service(
            VersionDeleter { _, path, _, _ ->
                calls += "first:$path"
                if (secondRun == null) {
                    // when: the second instance is asked to run the policy while the first one is still deleting
                    secondRun = second.run("keep-one", actor, CleanupTrigger.MANUAL)
                }
                Result.ok(Unit)
            },
        )
        second = service(VersionDeleter { _, path, _, _ -> calls += "second:$path"; Result.ok(Unit) })

        first.run("keep-one", actor, CleanupTrigger.MANUAL)

        // then: the second run is refused and deletes nothing
        assertThat(secondRun).`as`("result of the second run").isNotNull()
        assertThat(secondRun!!.isErr)
            .`as`("a second run of a live policy on another instance must be refused, but it was accepted")
            .isTrue()
        assertThat(calls)
            .`as`("deletions: the second instance must not delete while the first run is live")
            .containsExactly("first:net/example/app/1.0")
    }

    private fun storage(): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve("releases").createDirectories(), "100%")

    private fun StorageProvider.writeVersion(artifact: String, version: String) {
        putFile(Location.of("$artifact/$version/app-$version.pom"), "pom".byteInputStream())
        putFile(Location.of("$artifact/$version/app-$version.jar"), "jar-content".byteInputStream())
    }

    private fun service(deleter: VersionDeleter) = CleanupService(
        journalist = logger,
        clock = clock,
        policies = { listOf(policy) },
        targets = { listOf(CleanupTarget(name = "releases", storage = storage(), storesMirrors = false, cachedVersions = emptyMap())) },
        deleter = deleter,
        runs = InMemoryCleanupRunRepository(),
    )

}
