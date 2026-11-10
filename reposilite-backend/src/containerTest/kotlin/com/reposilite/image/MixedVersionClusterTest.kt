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

package com.reposilite.image

import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The previous release and the current build beside each other on one MariaDB database and one repository directory,
 * which is the state of a rolling upgrade while the replicas are replaced one after the other.
 *
 * The order is the one a rolling upgrade produces: the previous release is already running when the current build joins
 * the database, and the previous release is the one that was started first. Every case gets its own database, its own
 * directory and its own replicas, so no case can see what another one wrote.
 *
 * Requests authenticate with the bootstrap token that both releases accept from `--token`, so no case depends on a
 * persistent token that only one release could have written.
 */
class MixedVersionClusterTest {

    @Test
    @DisplayName("deploys cross between the releases")
    fun `deploys cross between the releases`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                previous.startOrExplain()
                sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                    current.startOrExplain()
                    val oldUrl = previous.baseUrl()
                    val newUrl = current.baseUrl()

                    // One artifact in each direction, one after the other.
                    deploy(oldUrl, FROM_OLD_TO_NEW, FROM_OLD_TO_NEW)
                    assertServes(newUrl, FROM_OLD_TO_NEW, reason = "the current build reads what the previous release deployed")

                    deploy(newUrl, FROM_NEW_TO_OLD, FROM_NEW_TO_OLD)
                    assertServes(oldUrl, FROM_NEW_TO_OLD, reason = "the previous release reads what the current build deployed")

                    // Two different artifacts at the same moment, one through each replica.
                    deployConcurrently(
                        oldUrl to CONCURRENT_FROM_OLD,
                        newUrl to CONCURRENT_FROM_NEW,
                    )
                    listOf(CONCURRENT_FROM_OLD, CONCURRENT_FROM_NEW).forEach { path ->
                        assertServes(oldUrl, path, reason = "the previous release reads a concurrent deploy")
                        assertServes(newUrl, path, reason = "the current build reads a concurrent deploy")
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the previous release restarts and serves once the current build has changed the schema")
    fun `the old replica restarts after the schema changed`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            // The current build keeps running for the whole case, so the restart happens beside a changed schema.
            sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                    previous.startOrExplain()
                    deploy(previous.baseUrl(), BEFORE_RESTART, BEFORE_RESTART)

                    current.startOrExplain()
                    assertServes(current.baseUrl(), BEFORE_RESTART, reason = "the current build reads the old deploy")
                }

                // The old replica is replaced by a new process on the same database and directory.
                sharedRepositoryReplica(ImageUnderTest.previous, state).use { restarted ->
                    restarted.startOrExplain()
                    val url = restarted.baseUrl()

                    val tokens = Http.get("$url/api/tokens", MIXED_BOOTSTRAP)
                    assertThat(tokens.isSuccessful)
                        .describedAs("the restarted previous release answers an authenticated request: status %s, body %s", tokens.status, tokens.body)
                        .isTrue()
                    assertServes(url, BEFORE_RESTART, reason = "the restarted previous release reads the old deploy")
                    deploy(url, AFTER_RESTART, AFTER_RESTART)
                    assertServes(current.baseUrl(), AFTER_RESTART, reason = "the current build reads a deploy of the restarted release")
                }
            }
        }
    }

    @Test
    @DisplayName("the columns the current build adds can be written by the previous release")
    fun `columns the current build adds stay writable by the previous release`(@TempDir directory: Path) {
        withSharedState(directory) { state ->
            val afterPrevious: List<SchemaColumn>
            val afterCurrent: List<SchemaColumn>
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { previous ->
                previous.startOrExplain()
                afterPrevious = state.database.columns()

                sharedRepositoryReplica(ImageUnderTest.server, state).use { current ->
                    current.startOrExplain()
                    afterCurrent = state.database.columns()
                }
            }
            assertNoIncompatibleChange(afterPrevious, afterCurrent, "the current build started on the previous release's schema")

            // The previous release is restarted on the schema the current build left behind.
            sharedRepositoryReplica(ImageUnderTest.previous, state).use { restarted ->
                restarted.startOrExplain()
                assertNoIncompatibleChange(afterCurrent, state.database.columns(), "the previous release restarted")
            }
        }
    }

    /**
     * What the previous release can still work with, checked on two listings of the database. A column the first listing
     * had is not removed, does not change its type, and does not become required. A column the second listing added to a
     * table the first listing had must not be required either, because the previous release inserts into that table
     * without it.
     *
     * A table the first listing did not have is not checked. The previous release does not know it and never writes to
     * it, and the design allows new tables with any columns. Its columns are the current build's own business.
     */
    private fun assertNoIncompatibleChange(before: List<SchemaColumn>, after: List<SchemaColumn>, transition: String) {
        val known = before.associateBy { it.key }
        val knownTables = before.map { it.table }.toSet()
        val current = after.associateBy { it.key }

        assertThat(known.keys - current.keys)
            .describedAs("%s: columns that were removed", transition)
            .isEmpty()
        assertThat(known.values.filter { current.getValue(it.key).type != it.type }.map { it.key })
            .describedAs("%s: columns whose type changed", transition)
            .isEmpty()
        assertThat(known.values.filter { !it.requiresValue && current.getValue(it.key).requiresValue }.map { it.key })
            .describedAs("%s: columns that became NOT NULL without a default", transition)
            .isEmpty()
        assertThat(current.values.filter { it.table in knownTables && it.key !in known && it.requiresValue }.map { it.key })
            .describedAs("%s: columns added to an existing table that are NOT NULL without a default", transition)
            .isEmpty()
    }

    /** Puts [content] at [path] through the replica at [url]. The content is the path, so every artifact is distinct. */
    private fun deploy(url: String, path: String, content: String) {
        val response = Http.put("$url/$path", content, MIXED_BOOTSTRAP)
        assertThat(response.isSuccessful)
            .describedAs("deploy %s: status %s, body %s", path, response.status, response.body)
            .isTrue()
    }

    /** Reads [path] through the replica at [url] and expects the bytes that were deployed for it. */
    private fun assertServes(url: String, path: String, reason: String) {
        val response = Http.get("$url/$path", MIXED_BOOTSTRAP)
        assertThat(response.status).describedAs("%s: status of %s", reason, path).isEqualTo(200)
        assertThat(response.body).describedAs("%s: content of %s", reason, path).isEqualTo(path)
    }

    /**
     * Starts both deploys behind one latch, so they reach their replicas at the same moment, and waits for both within
     * [WAIT_BOUND]. A latch instead of a sleep: the test does not depend on how long a request takes.
     */
    private fun deployConcurrently(vararg deploys: Pair<String, String>) {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(deploys.size)
        try {
            val responses = deploys.map { (url, path) ->
                pool.submit(
                    Callable {
                        start.await()
                        Http.put("$url/$path", path, MIXED_BOOTSTRAP)
                    }
                )
            }
            start.countDown()
            responses.forEachIndexed { index, future ->
                val (_, path) = deploys[index]
                val response = future.get(WAIT_BOUND.toSeconds(), TimeUnit.SECONDS)
                assertThat(response.isSuccessful)
                    .describedAs("concurrent deploy %s: status %s, body %s", path, response.status, response.body)
                    .isTrue()
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private companion object {
        val WAIT_BOUND: java.time.Duration = java.time.Duration.ofSeconds(60)

        const val FROM_OLD_TO_NEW = "releases/com/example/mixed/1.0.0/mixed-1.0.0.jar"
        const val FROM_NEW_TO_OLD = "releases/com/example/mixed/2.0.0/mixed-2.0.0.jar"
        const val CONCURRENT_FROM_OLD = "releases/com/example/mixed/3.0.0/mixed-3.0.0.jar"
        const val CONCURRENT_FROM_NEW = "releases/com/example/mixed/4.0.0/mixed-4.0.0.jar"
        const val BEFORE_RESTART = "releases/com/example/mixed/5.0.0/mixed-5.0.0.jar"
        const val AFTER_RESTART = "releases/com/example/mixed/6.0.0/mixed-6.0.0.jar"
    }
}
