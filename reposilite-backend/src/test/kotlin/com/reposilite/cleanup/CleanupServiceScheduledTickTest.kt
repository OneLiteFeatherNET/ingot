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
import com.reposilite.scheduling.ScheduledJobClaims
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * The service exposes one scheduler tick, the same tick the 30 s timer runs, so that a caller can run it without waiting.
 */
internal class CleanupServiceScheduledTickTest {

    @Test
    fun `should run one scheduler tick when the service tick is called`() {
        // given: a daily policy due at 02:00 and a fixed clock at that minute, with the scheduler wired to the service
        val clock = Clock.fixed(Instant.parse("2026-10-15T02:00:00Z"), ZoneOffset.UTC)
        val policy = CleanupPolicy(
            id = "nightly",
            enabled = true,
            repositories = listOf("releases"),
            scope = CleanupScope.RELEASES,
            keepLatest = 1,
            schedule = "02:00",
        )
        val started = mutableListOf<String>()
        val scheduler = CleanupScheduler(
            clock = clock,
            policies = { listOf(policy) },
            start = { due, _ -> started += due.id },
            claims = ScheduledJobClaims.Unshared,
            journalist = InMemoryLogger(),
        )
        val service = CleanupService(
            journalist = InMemoryLogger(),
            clock = clock,
            policies = { listOf(policy) },
            targets = { emptyList() },
            deleter = VersionDeleter { _, _, _, _ -> error("the scheduler tick must not delete anything itself") },
            runs = InMemoryCleanupRunRepository(),
            scheduledTick = { scheduler.runTick() },
        )

        // when: the service tick is called directly
        service.runScheduledTick()

        // then: the due policy was started exactly once
        assertEquals(listOf("nightly"), started, "runScheduledTick must run one scheduler tick, so the due policy starts once")
    }

}
