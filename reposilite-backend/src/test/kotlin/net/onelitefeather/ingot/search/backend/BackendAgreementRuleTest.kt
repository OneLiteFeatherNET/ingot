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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The cluster rule of the backend id (search backend registration, D5), decided on plain values. */
internal class BackendAgreementRuleTest {

    private val now = Instant.parse("2026-10-16T10:00:00Z")

    @Test
    fun `a replica of the stored backend drains`() {
        val decision = BackendAgreementRule.decide(stored = "database", own = "database", lastSeenOfStored = now, now = now)

        assertThat(decision).`as`("decision of the replica whose id is stored").isEqualTo(AgreementDecision.DRAIN)
    }

    @Test
    fun `a replica of another backend does not drain while the stored backend is seen`() {
        val decision = BackendAgreementRule.decide(stored = "database", own = "opensearch", lastSeenOfStored = now, now = now)

        assertThat(decision).`as`("decision of a replica with another id while the stored one is seen").isEqualTo(AgreementDecision.MISMATCH)
    }

    @Test
    fun `an absent stored id is claimed by the first available replica`() {
        val decision = BackendAgreementRule.decide(stored = null, own = "opensearch", lastSeenOfStored = null, now = now)

        assertThat(decision).`as`("decision while no id is stored").isEqualTo(AgreementDecision.CLAIM)
    }

    @Test
    fun `a heartbeat younger than three minutes blocks the switch`() {
        val decision = BackendAgreementRule.decide(
            stored = "database",
            own = "opensearch",
            lastSeenOfStored = now.minus(Duration.ofMinutes(3)).plusSeconds(1),
            now = now,
        )

        assertThat(decision).`as`("decision with a heartbeat of 2 minutes 59 seconds").isEqualTo(AgreementDecision.MISMATCH)
    }

    @Test
    fun `a heartbeat exactly three minutes old allows the switch`() {
        val decision = BackendAgreementRule.decide(
            stored = "database",
            own = "opensearch",
            lastSeenOfStored = now.minus(Duration.ofMinutes(3)),
            now = now,
        )

        assertThat(decision).`as`("decision with a heartbeat of exactly 3 minutes").isEqualTo(AgreementDecision.SWITCH)
    }

    @Test
    fun `a stored id that no replica has seen allows the switch`() {
        val decision = BackendAgreementRule.decide(stored = "database", own = "opensearch", lastSeenOfStored = null, now = now)

        assertThat(decision).`as`("decision when no heartbeat of the stored id exists").isEqualTo(AgreementDecision.SWITCH)
    }

}
