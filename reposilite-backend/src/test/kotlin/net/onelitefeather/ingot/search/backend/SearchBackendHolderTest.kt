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

package net.onelitefeather.ingot.search.backend

import com.reposilite.audit.MutableClock
import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The transitions of a backend's state with a fixed-step clock and the fake backend (search backend registration, D4). */
internal class SearchBackendHolderTest {

    private val start = Instant.parse("2026-10-16T10:00:00Z")
    private val clock = MutableClock(start)
    private val logs = mutableListOf<String>()
    private val fake = FakeArtifactSearchBackend()
    private val factory = object : ArtifactSearchBackendFactory {
        override val id: String = "fake"
        override val apiVersion: Int = SEARCH_BACKEND_API_VERSION
        override fun create(context: SearchBackendContext): ArtifactSearchBackend = fake
    }

    /** How many times the holder asked for a backend. */
    private var opens = 0

    /** The first [failuresBeforeOpen] opens throw, the later ones return the fake backend. */
    private var failuresBeforeOpen = 0

    private fun holder(resolution: BackendResolution = BackendResolution.Installed(factory)) = SearchBackendHolder(
        resolution = resolution,
        clock = clock,
        log = logs::add,
        createBackend = {
            opens++
            if (opens <= failuresBeforeOpen) throw IllegalStateException("cannot reach the service")
            fake
        },
    )

    @Test
    fun `an open that fails is logged once and retried on the next thirty-second tick`() {
        failuresBeforeOpen = 2
        val holder = holder()

        holder.tick()
        assertThat(holder.state).`as`("state after the first failed open").isEqualTo(SearchBackendState.OPEN_FAILED)

        clock.advance(Duration.ofSeconds(30))
        holder.tick()
        assertThat(opens).`as`("opens after the second tick").isEqualTo(2)
        assertThat(holder.state).`as`("state after the second failed open").isEqualTo(SearchBackendState.OPEN_FAILED)

        clock.advance(Duration.ofSeconds(30))
        holder.tick()

        assertThat(holder.state).`as`("state once the backend opened").isEqualTo(SearchBackendState.AVAILABLE)
        assertThat(opens).`as`("opens until the backend opened").isEqualTo(3)
        assertThat(logs).`as`("log lines: the failure once, then the availability").hasSize(2)
    }

    @Test
    fun `an open that failed is not retried before thirty seconds have passed`() {
        failuresBeforeOpen = 1
        val holder = holder()
        holder.tick()

        clock.advance(Duration.ofSeconds(10))
        holder.tick()
        assertThat(opens).`as`("opens ten seconds after a failure").isEqualTo(1)

        clock.advance(Duration.ofSeconds(20))
        holder.tick()
        assertThat(opens).`as`("opens thirty seconds after a failure").isEqualTo(2)
    }

    @Test
    fun `a backend that reports down is logged once, not on every health check`() {
        fake.healthAnswer = BackendHealth.Down("unreachable")
        val holder = holder()
        holder.tick()

        clock.advance(Duration.ofSeconds(10))
        holder.tick()
        clock.advance(Duration.ofSeconds(10))
        holder.tick()

        assertThat(holder.state).`as`("state of a backend that stays down").isEqualTo(SearchBackendState.DOWN)
        assertThat(logs).`as`("log lines while the backend stays down").hasSize(1)
    }

    @Test
    fun `a drain of a down backend waits for its backoff`() {
        fake.healthAnswer = BackendHealth.Down("unreachable")
        val holder = holder()
        holder.tick()
        assertThat(holder.drainAllowed()).`as`("first drain of a down backend").isTrue()

        holder.drainFailed()
        clock.advance(Duration.ofSeconds(9))
        assertThat(holder.drainAllowed()).`as`("drain before the backoff has passed").isFalse()

        clock.advance(Duration.ofSeconds(1))
        assertThat(holder.drainAllowed()).`as`("drain once the first backoff has passed").isTrue()
        assertThat(OutboxPolicy.retryAt(start, 1)).`as`("the backoff the holder waits for").isEqualTo(start.plusSeconds(10))
    }

    @Test
    fun `a backend that recovers from down becomes available and is logged`() {
        fake.healthAnswer = BackendHealth.Down("unreachable")
        val holder = holder()
        holder.tick()

        fake.healthAnswer = BackendHealth.Up
        clock.advance(Duration.ofSeconds(10))
        holder.tick()

        assertThat(holder.state).`as`("state after recovery").isEqualTo(SearchBackendState.AVAILABLE)
        assertThat(holder.drainAllowed()).`as`("drain of a recovered backend").isTrue()
        assertThat(logs).`as`("log lines: the down state, then the recovery").hasSize(2)
    }

    @Test
    fun `a healthy backend is checked every ten seconds and not on every tick`() {
        val holder = holder()
        holder.tick()
        assertThat(fake.healthChecks).`as`("health checks right after the open").isEqualTo(1)

        clock.advance(Duration.ofSeconds(5))
        holder.tick()
        assertThat(fake.healthChecks).`as`("health checks five seconds later").isEqualTo(1)

        clock.advance(Duration.ofSeconds(5))
        holder.tick()
        assertThat(fake.healthChecks).`as`("health checks ten seconds later").isEqualTo(2)
    }

    @Test
    fun `a backend that is not installed is never opened`() {
        val holder = holder(BackendResolution.Unavailable(SearchBackendState.NOT_INSTALLED, "no search backend is registered"))

        clock.advance(Duration.ofMinutes(30))
        holder.tick()

        assertThat(opens).`as`("opens of a backend that is not installed").isZero()
        assertThat(holder.state).`as`("state of a backend that is not installed").isEqualTo(SearchBackendState.NOT_INSTALLED)
        assertThat(holder.drainAllowed()).`as`("drain of a backend that is not installed").isFalse()
    }

    @Test
    fun `the holder opens the backend it creates before it checks the health`() {
        val holder = holder()
        holder.tick()

        assertThat(fake.openCalls).`as`("open calls of the backend the holder created").isEqualTo(1)
        assertThat(holder.state).`as`("state after the open").isEqualTo(SearchBackendState.AVAILABLE)
    }

    @Test
    fun `an open that throws from the backend is a failed open and the backend is not kept`() {
        fake.openFailure = IllegalStateException("index not reachable")
        val holder = holder()
        holder.tick()

        assertThat(holder.state).`as`("state when the backend's open throws").isEqualTo(SearchBackendState.OPEN_FAILED)
        assertThat(holder.backendOrNull()).`as`("backend of a failed open").isNull()
    }

    @Test
    fun `a failed open is retried on the backend that was created first`() {
        var created = 0
        fake.openFailure = IllegalStateException("index not reachable")
        val holder = SearchBackendHolder(
            resolution = BackendResolution.Installed(factory),
            clock = clock,
            log = logs::add,
            createBackend = {
                created++
                fake
            },
        )
        holder.tick()
        fake.openFailure = null
        clock.advance(Duration.ofSeconds(30))
        holder.tick()

        assertThat(created).`as`("backends created across a failed open and its retry").isEqualTo(1)
        assertThat(holder.state).`as`("state after the retry opened the backend").isEqualTo(SearchBackendState.AVAILABLE)
    }

    @Test
    fun `the outbox is not drained while the backend cannot open`() {
        failuresBeforeOpen = 1
        val holder = holder()
        holder.tick()

        assertThat(holder.drainAllowed()).`as`("drain while the open failed").isFalse()
    }

}
