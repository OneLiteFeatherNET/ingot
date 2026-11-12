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

import net.onelitefeather.ingot.search.outbox.OutboxPolicy
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Holds the backend of this replica and its state (search backend registration, D4). The poller and the routes read
 * [state]; [tick] moves it. A transition is logged once when it happens, never on every tick.
 *
 * - An installed factory is created and opened on the first tick. A failed open is retried after [OPEN_RETRY].
 * - A backend that is open is health checked every [HEALTH_INTERVAL]. A down backend may still be drained, but only after
 *   the backoff of [OutboxPolicy.retryAt] that follows each failed drain.
 * - A state that is not installed or refused never changes.
 *
 * [createBackend] creates the backend of a factory. It is called once; a failed open keeps that backend for the next attempt,
 * which opens it again. The holder opens it itself; a throw from either counts as a failed open.
 */
internal class SearchBackendHolder(
    resolution: BackendResolution,
    private val clock: Clock,
    private val log: (String) -> Unit,
    private val createBackend: (ArtifactSearchBackendFactory) -> ArtifactSearchBackend,
) {

    private val factory: ArtifactSearchBackendFactory?

    /** The state of the backend. Read by the poller and the routes. */
    var state: SearchBackendState
        private set

    /** The reason of the current state, or the health message of a down backend. Empty while available. */
    var detail: String
        private set

    private var backend: ArtifactSearchBackend? = null
    private var nextOpenAt: Instant = clock.instant()
    private var nextHealthAt: Instant = clock.instant()
    private var nextDrainAt: Instant = clock.instant()
    private var drainFailures = 0
    private var nextReportAt: Instant = clock.instant()

    init {
        when (resolution) {
            is BackendResolution.Installed -> {
                factory = resolution.factory
                state = SearchBackendState.OPEN_FAILED
                detail = NOT_OPENED_YET
            }
            is BackendResolution.Unavailable -> {
                factory = null
                state = resolution.state
                detail = redactBackendDetail(resolution.reason)
                report()
            }
        }
    }

    /**
     * Runs what is due at the current time: an open that failed, a health check, or the report of a backend that is not
     * installed or refused. Called every few seconds by the scheduler.
     */
    @Synchronized
    fun tick() {
        val now = clock.instant()
        when (state) {
            SearchBackendState.OPEN_FAILED -> if (factory != null && !now.isBefore(nextOpenAt)) open(factory, now)
            SearchBackendState.AVAILABLE, SearchBackendState.DOWN -> if (!now.isBefore(nextHealthAt)) checkHealth(now)
            SearchBackendState.NOT_INSTALLED, SearchBackendState.REFUSED -> if (!now.isBefore(nextReportAt)) report()
            else -> Unit
        }
    }

    /** The backend, when it is available or down. A down backend answers drains only, as [drainAllowed] says. */
    fun backendOrNull(): ArtifactSearchBackend? = if (state == SearchBackendState.AVAILABLE || state == SearchBackendState.DOWN) backend else null

    /** Whether the poller may drain the outbox now: always when available, at most once per backoff when down, never otherwise. */
    fun drainAllowed(): Boolean = when (state) {
        SearchBackendState.AVAILABLE -> true
        SearchBackendState.DOWN -> !clock.instant().isBefore(nextDrainAt)
        else -> false
    }

    /** Records a drain of a down backend that failed. The next drain waits for the backoff that grows with each failure. */
    fun drainFailed() {
        if (state != SearchBackendState.DOWN) return
        drainFailures++
        nextDrainAt = OutboxPolicy.retryAt(clock.instant(), drainFailures)
    }

    private fun open(factory: ArtifactSearchBackendFactory, now: Instant) {
        try {
            val created = backend ?: createBackend(factory).also { backend = it }
            created.open()
        } catch (failure: Exception) {
            nextOpenAt = now.plus(OPEN_RETRY)
            transition(SearchBackendState.OPEN_FAILED, failure.message ?: failure.javaClass.simpleName)
            return
        }
        checkHealth(now)
    }

    private fun checkHealth(now: Instant) {
        nextHealthAt = now.plus(HEALTH_INTERVAL)
        when (val answer = runCatching { backend?.health() ?: BackendHealth.Down("the backend is not open") }
            .getOrElse { BackendHealth.Down(it.message ?: it.javaClass.simpleName) }) {
            BackendHealth.Up -> {
                drainFailures = 0
                transition(SearchBackendState.AVAILABLE, "")
            }
            is BackendHealth.Down -> {
                if (state != SearchBackendState.DOWN) nextDrainAt = now
                transition(SearchBackendState.DOWN, answer.message)
            }
        }
    }

    private fun transition(newState: SearchBackendState, newDetail: String) {
        val shown = redactBackendDetail(newDetail)
        if (newState == state && shown == detail) return
        state = newState
        detail = shown
        log("SEARCH | state $newState${if (shown.isEmpty()) "" else ": $shown"}")
    }

    /** Logs a backend that is not installed or refused, now and every five minutes, so the cause stays visible in the log. */
    private fun report() {
        nextReportAt = clock.instant().plus(REPORT_INTERVAL)
        log("SEARCH | state $state: $detail")
    }

    companion object {
        /** How long a failed open waits before the next attempt. */
        val OPEN_RETRY: Duration = Duration.ofSeconds(30)

        /** How often an open backend is health checked. */
        val HEALTH_INTERVAL: Duration = Duration.ofSeconds(10)

        /** How often a backend that is not installed or refused is reported again. */
        val REPORT_INTERVAL: Duration = Duration.ofMinutes(5)

        private const val NOT_OPENED_YET = "not opened yet"
    }

}
