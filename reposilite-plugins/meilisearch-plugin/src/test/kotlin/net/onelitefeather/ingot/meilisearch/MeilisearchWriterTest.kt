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

import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.backend.BackendBusyException
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import net.onelitefeather.ingot.search.database.pathHash
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The write path (design D4, D5): tasks are awaited within the bound, a failed task fails its batch, a task still running after
 * the bound releases the batch as busy, and the order of the actions is kept. No test sleeps: the waiter advances a manual clock.
 */
internal class MeilisearchWriterTest {

    private val environment = mapOf(
        "INGOT_MEILISEARCH_URL" to "https://search.example.test",
        "INGOT_MEILISEARCH_API_KEY" to "test-key",
        "INGOT_MEILISEARCH_TASK_WAIT_SECONDS" to "20",
    )

    private fun upsert(path: String): IndexAction.Upsert = IndexAction.Upsert(
        IndexRow(
            file = ArtifactFileRecord(
                repository = "releases",
                path = path,
                groupId = "com.example",
                artifactId = "app",
                version = "1.0",
                classifier = null,
                extension = "jar",
                snapshot = false,
            ),
            provenance = Provenance.HOSTED,
            sizeBytes = 10,
            lastModified = 1_000,
            sha1 = null,
            sha256 = null,
            indexedAt = 2_000,
        ),
    )

    private class Harness(
        val clock: ManualClock,
        val exchange: FakeMeilisearch,
        val waiter: ManualWaiter,
    )

    private fun backendWith(
        answers: FakeMeilisearch.() -> Unit,
        waitStep: Duration = Duration.ZERO,
    ): Pair<net.onelitefeather.ingot.meilisearch.MeilisearchSearchBackend, Harness> {
        val clock = ManualClock()
        val exchange = FakeMeilisearch(clock).apply(answers)
        val waiter = ManualWaiter(clock, waitStep)
        val backend = MeilisearchSearchBackend(
            MeilisearchSettings.read(environment, { error("no file expected") }),
            clock,
            exchange,
            waiter,
        )
        return backend to Harness(clock, exchange, waiter)
    }

    @Test
    fun `should send the upserts of a batch and return once every task succeeded`() {
        // given: every task succeeds at its first poll
        val (backend, harness) = backendWith({
            onDocuments(taskUid = 7)
            onTask(7, status = "succeeded")
        })

        // when: a batch of two upserts is applied
        backend.apply(listOf(upsert("com/example/app/1.0/app-1.0.jar"), upsert("com/example/app/1.0/app-1.0.pom")))

        // then: one document request carries both rows, and the task was polled until it succeeded
        assertThat(harness.exchange.calls.count { it.method == "POST" && it.uri.path.endsWith("/documents") })
            .`as`("document requests of one batch of two upserts")
            .isEqualTo(1)
        assertThat(harness.exchange.taskPolls(7)).`as`("polls of task 7").isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should fail the batch with the server's error code when a task fails`() {
        // given: the task fails with a document error
        val (backend, _) = backendWith({
            onDocuments(taskUid = 8)
            onTask(8, status = "failed", errorCode = "missing_document_id")
        })

        // when / then: the batch fails and names the code of the server
        assertThatThrownBy { backend.apply(listOf(upsert("com/example/app/1.0/app-1.0.jar"))) }
            .isInstanceOf(MeilisearchRejectedException::class.java)
            .hasMessageContaining("missing_document_id")
    }

    @Test
    fun `should release the batch as busy when a task is still processing after the bound`() {
        // given: the task never finishes, and each poll takes five seconds
        val (backend, _) = backendWith(
            {
                onDocuments(taskUid = 9)
                onTask(9, status = "processing")
            },
            waitStep = Duration.ofSeconds(5),
        )

        // when / then: the batch is busy with the retry delay of the design, and no attempt is counted
        assertThatThrownBy { backend.apply(listOf(upsert("com/example/app/1.0/app-1.0.jar"))) }
            .isInstanceOf(BackendBusyException::class.java)
            .satisfies({ busy ->
                assertThat((busy as BackendBusyException).retryAfter)
                    .`as`("retry delay of a batch that outlives the bound")
                    .isEqualTo(Duration.ofSeconds(10))
            })
    }

    @Test
    fun `should keep the order of the actions across requests`() {
        // given: an upsert, a removal and another upsert, each answered by its own task
        val (backend, harness) = backendWith({
            onDocuments(taskUid = 10)
            onDelete(taskUid = 11)
            onDocuments(taskUid = 12)
            onTask(10, status = "succeeded")
            onTask(11, status = "succeeded")
            onTask(12, status = "succeeded")
        })

        // when: the three actions are applied in one batch
        backend.apply(
            listOf(
                upsert("com/example/app/1.0/app-1.0.jar"),
                IndexAction.RemovePath("releases", "com/example/old/1.0/old-1.0.jar"),
                upsert("com/example/app/1.0/app-1.0.pom"),
            ),
        )

        // then: the requests reach the server in the order of the actions
        assertThat(harness.exchange.calls.filter { it.method != "GET" }.map { it.method + " " + it.uri.path.substringAfter("/indexes/ingot-artifacts") })
            .`as`("write requests in order")
            .containsExactly("POST /documents", "DELETE /documents/${pathHash("releases", "com/example/old/1.0/old-1.0.jar")}", "POST /documents")
    }

}
