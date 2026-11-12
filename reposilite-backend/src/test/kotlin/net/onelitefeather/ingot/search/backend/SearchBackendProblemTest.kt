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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/** The problem answer of a backend that is not available, and the redaction of what it shows (search backend registration, D4). */
internal class SearchBackendProblemTest {

    private val notAvailable = listOf(
        SearchBackendState.NOT_INSTALLED,
        SearchBackendState.REFUSED,
        SearchBackendState.OPEN_FAILED,
        SearchBackendState.DOWN,
        SearchBackendState.CLUSTER_MISMATCH,
    )

    @Test
    fun `every state that is not available answers 503`() {
        notAvailable.forEach { state ->
            val problem = backendUnavailableProblem("opensearch", state, "the reason")

            assertThat(problem.status).`as`("status of the problem in state $state").isEqualTo(503)
        }
    }

    @Test
    fun `the problem is titled as a backend that is not available`() {
        val problem = backendUnavailableProblem("opensearch", SearchBackendState.OPEN_FAILED, "connection refused")

        assertThat(problem.title).`as`("title of the problem").isEqualTo("Search backend not available")
    }

    @Test
    fun `the detail names the backend and the reason`() {
        val problem = backendUnavailableProblem("opensearch", SearchBackendState.DOWN, "the cluster is red")

        assertThat(problem.detail).`as`("detail of the problem").contains("opensearch").contains("the cluster is red")
    }

    @Test
    fun `user information of a url in the reason is removed from the detail`() {
        val problem = backendUnavailableProblem(
            "opensearch",
            SearchBackendState.OPEN_FAILED,
            "cannot reach https://admin:hunter2@search.example:9200/index",
        )

        assertThat(problem.detail).`as`("detail of the problem").doesNotContain("hunter2").contains("search.example:9200/index")
    }

    @Test
    fun `a secret read through the context is replaced in the problem detail`() {
        val environment = mapOf("SEARCH_TOKEN" to "s3cr3t-token")
        val context = CoreSearchBackendContext(
            MutableClock(Instant.parse("2026-10-16T10:00:00Z")),
            "instance",
            "opensearch",
            environment::get,
        ) { _, _ -> }
        context.secret("SEARCH_TOKEN")

        val problem = backendUnavailableProblem(
            "opensearch",
            SearchBackendState.OPEN_FAILED,
            "login with s3cr3t-token refused",
            redact = context::redact,
        )

        assertThat(problem.detail).`as`("detail of the problem").doesNotContain("s3cr3t-token").contains("login with ***")
    }

    @Test
    fun `an empty secret causes no replacement in the problem detail`() {
        val context = CoreSearchBackendContext(
            MutableClock(Instant.parse("2026-10-16T10:00:00Z")),
            "instance",
            "opensearch",
            emptyMap<String, String>()::get,
        ) { _, _ -> }
        context.secret("MISSING_TOKEN")

        val problem = backendUnavailableProblem(
            "opensearch",
            SearchBackendState.DOWN,
            "the cluster is red",
            redact = context::redact,
        )

        assertThat(problem.detail).`as`("detail of the problem").contains("the cluster is red")
    }

    @Test
    fun `redaction keeps the scheme, the host and the path of a url`() {
        val redacted = redactBackendDetail("jdbc:postgresql://reader:secret@db.example:5432/search")

        assertThat(redacted).`as`("redacted text").isEqualTo("jdbc:postgresql://***@db.example:5432/search")
    }

    @Test
    fun `text without user information is left as it is`() {
        val text = "no search backend is registered for 'opensearch'"

        assertThat(redactBackendDetail(text)).`as`("redacted text").isEqualTo(text)
    }

}
