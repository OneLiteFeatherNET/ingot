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

import com.reposilite.ReposiliteObjectMapper
import net.onelitefeather.ingot.search.ArtifactFileRecord
import net.onelitefeather.ingot.search.IndexRow
import net.onelitefeather.ingot.search.Provenance
import net.onelitefeather.ingot.search.SearchUnavailableException
import net.onelitefeather.ingot.search.backend.BackendHealth
import net.onelitefeather.ingot.search.backend.ExperimentalIngotSearchApi
import net.onelitefeather.ingot.search.backend.IndexAction
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration

/** The open, health, status and redaction behaviour of the backend, against the in-process fake server. No sleeps, no network. */
internal class MeilisearchBackendTest {

    private val key = "secret-key-42"
    private val host = "search.example.test"
    private val index = "ingot-artifacts"

    private fun environment(): Map<String, String> = mapOf(
        "INGOT_MEILISEARCH_URL" to "https://$host",
        "INGOT_MEILISEARCH_API_KEY" to key,
    )

    private class Fixture(val backend: MeilisearchSearchBackend, val server: FakeMeilisearch, val clock: ManualClock, val logged: MutableList<String>)

    private fun fixture(
        waitStep: Duration = Duration.ZERO,
        setup: FakeMeilisearch.() -> Unit,
    ): Fixture {
        val clock = ManualClock()
        val server = FakeMeilisearch(clock).apply(setup)
        val logged = mutableListOf<String>()
        val backend = MeilisearchSearchBackend(
            MeilisearchSettings.read(environment(), { error("no key file expected") }),
            clock,
            server,
            ManualWaiter(clock, waitStep),
            logged::add,
        )
        return Fixture(backend, server, clock, logged)
    }

    /** A server of the supported version, with the index present and its settings as the plugin wants them. */
    private fun FakeMeilisearch.supportedWithSettings() {
        on("GET", "/version", body = """{"pkgVersion":"1.15.0"}""")
        on("GET", "/indexes/$index", body = """{"uid":"$index","createdAt":"2026-11-14T10:00:00Z"}""")
        on("GET", "/indexes/$index/settings", body = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(MeilisearchIndexSettings.desired))
    }

    @Test
    fun `should refuse to open a server of another minor version and name both versions`() {
        // given: a server of a minor version this plugin was not tested with
        val fixture = fixture { on("GET", "/version", body = """{"pkgVersion":"1.12.0"}""") }

        // when / then: the message names the server and the supported version
        assertThatThrownBy { fixture.backend.open() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("1.12.0")
            .hasMessageContaining("1.15")
    }

    @Test
    fun `should create the index when it is missing and wait for the creation task`() {
        // given: the index does not exist yet, and its creation succeeds
        val fixture = fixture {
            on("GET", "/version", body = """{"pkgVersion":"1.15.2"}""")
            on("GET", "/indexes/$index", status = 404, body = """{"code":"index_not_found"}""")
            on("POST", "/indexes", status = 202, body = """{"taskUid":40}""")
            onTask(40, "succeeded")
            on("GET", "/indexes/$index/settings", body = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(MeilisearchIndexSettings.desired))
        }

        // when: the backend is opened
        fixture.backend.open()

        // then: the index was created with the primary key id, and the task was awaited
        assertThat(fixture.server.calls.any { it.method == "POST" && it.uri.path == "/indexes" })
            .`as`("index creation sent on a missing index")
            .isTrue()
        assertThat(fixture.server.taskPolls(40)).`as`("polls of the creation task").isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `should log each changed setting with its old and new value before applying them`() {
        // given: the stored settings are empty, so every value differs
        val fixture = fixture {
            on("GET", "/version", body = """{"pkgVersion":"1.15.0"}""")
            on("GET", "/indexes/$index", body = """{"uid":"$index"}""")
            on("GET", "/indexes/$index/settings", body = """{}""")
            on("PATCH", "/indexes/$index/settings", status = 202, body = """{"taskUid":41}""")
            onTask(41, "succeeded")
        }

        // when: the backend is opened
        fixture.backend.open()

        // then: the change of the filterable attributes is logged with the new value
        assertThat(fixture.logged)
            .`as`("log lines of the settings change")
            .anySatisfy { line -> assertThat(line).contains("filterableAttributes").contains("group_id") }
    }

    @Test
    fun `should keep a settings task that outlives the bound pending and refuse searches meanwhile`() {
        // given: the settings change is still processing after the wait bound of twenty seconds
        val fixture = fixture(waitStep = Duration.ofSeconds(25)) {
            on("GET", "/version", body = """{"pkgVersion":"1.15.0"}""")
            on("GET", "/indexes/$index", body = """{"uid":"$index"}""")
            on("GET", "/indexes/$index/settings", body = """{}""")
            on("PATCH", "/indexes/$index/settings", status = 202, body = """{"taskUid":42}""")
            onTask(42, "processing")
        }
        fixture.backend.open()

        // when / then: the health is down with the reason, and a search is refused with the same reason
        assertThat(fixture.backend.health()).isEqualTo(BackendHealth.Down("settings task running"))
        assertThatThrownBy {
            fixture.backend.search(net.onelitefeather.ingot.search.ArtifactSearchQuery(), net.onelitefeather.ingot.search.access.SearchAccess.NONE)
        }
            .isInstanceOf(SearchUnavailableException::class.java)
            .hasMessageContaining("reindexed")
    }

    @Test
    fun `should report up when the server answers available`() {
        // given: a supported server that reports itself as available
        val fixture = fixture {
            supportedWithSettings()
            on("GET", "/health", body = """{"status":"available"}""")
        }
        fixture.backend.open()

        // when / then
        assertThat(fixture.backend.health()).isEqualTo(BackendHealth.Up)
    }

    @Test
    fun `should report down with a reason when the server cannot be reached`() {
        // given: a server that is down
        val fixture = fixture { supportedWithSettings() }
        fixture.backend.open()
        fixture.server.unreachable()

        // when: the health is read
        val health = fixture.backend.health()

        // then: the state is down and the reason does not contain the key
        assertThat(health).isInstanceOf(BackendHealth.Down::class.java)
        assertThat((health as BackendHealth.Down).message).doesNotContain(key)
    }

    @Test
    fun `should not echo the key when the server answers an error that contains it`() {
        // given: the server refuses the documents and echoes the key in its message
        val fixture = fixture {
            supportedWithSettings()
            on("POST", "/indexes/$index/documents", status = 400, body = """{"message":"bad key $key","code":"invalid_document_fields"}""")
        }
        fixture.backend.open()
        val row = IndexAction.Upsert(
            IndexRow(
                file = ArtifactFileRecord("releases", "com/example/app/1.0/app-1.0.jar", "com.example", "app", "1.0", null, "jar", false),
                provenance = Provenance.HOSTED,
                sizeBytes = 1,
                lastModified = 1,
                sha1 = null,
                sha256 = null,
                indexedAt = 1,
            ),
        )

        // when / then: the refusal names the code, and the key stays out of the message
        assertThatThrownBy { fixture.backend.apply(listOf(row)) }
            .isInstanceOf(MeilisearchRejectedException::class.java)
            .hasMessageContaining("invalid_document_fields")
            .satisfies({ failure -> assertThat(failure.message.orEmpty()).doesNotContain(key) })
    }

    @Test
    fun `should not report the key or the host in the status properties`() {
        // given: a backend with a key and a host
        val fixture = fixture { supportedWithSettings() }

        // when / then: the properties of the status block carry neither
        assertThat(fixture.backend.properties().values.joinToString())
            .`as`("values of the status block")
            .doesNotContain(key)
            .doesNotContain(host)
    }

    @Test
    fun `should report the creation time of the index as its identity`() {
        // given: an index with a creation time
        val fixture = fixture { supportedWithSettings() }

        // when / then: the identity is the creation time
        assertThat(fixture.backend.indexIdentity()).isEqualTo("2026-11-14T10:00:00Z")
    }

}
