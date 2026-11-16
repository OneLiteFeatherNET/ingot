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

package net.onelitefeather.ingot.identity.session

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** The step-up rule as a pure decision of the instant it is given: no clock is read, so the boundaries are exact. */
internal class StepUpTest {

    private val now = Instant.parse("2026-11-14T10:00:00Z")
    private val window = Duration.ofMinutes(STEP_UP_MINUTES)

    private fun session(authenticatedAt: Instant?, mfa: Boolean) = VerifiedSession(
        principalId = 7,
        name = "alice",
        kind = "USER",
        familyId = "family-1",
        expiresAt = now.plusSeconds(900),
        authenticatedAt = authenticatedAt,
        mfa = mfa,
    )

    @Test
    fun `the step-up window is five minutes, the default of login mfa step-up minutes`() {
        assertThat(STEP_UP_MINUTES).describedAs("default of login.mfa.stepUpMinutes").isEqualTo(5L)
    }

    @Test
    fun `an authentication 299 seconds ago is within the step-up window`() {
        assertThat(isWithinStepUpWindow(now.minusSeconds(299), now, window)).isTrue()
    }

    @Test
    fun `an authentication 301 seconds ago is outside the step-up window`() {
        assertThat(isWithinStepUpWindow(now.minusSeconds(301), now, window)).isFalse()
    }

    @Test
    fun `a session without an authentication time is never within the step-up window`() {
        assertThat(isWithinStepUpWindow(null, now, window))
            .describedAs("a session token issued before the claim existed")
            .isFalse()
    }

    @Test
    fun `a user with a factor needs a second factor completed inside the window`() {
        val allowed = stepUpAllows(session(now.minusSeconds(10), mfa = false), hasFactor = true, enrolment = false, now = now, window = window)

        assertThat(allowed).describedAs("a fresh password without a second factor").isFalse()
    }

    @Test
    fun `a second factor completed inside the window satisfies a user with a factor`() {
        val allowed = stepUpAllows(session(now.minusSeconds(10), mfa = true), hasFactor = true, enrolment = false, now = now, window = window)

        assertThat(allowed).isTrue()
    }

    @Test
    fun `a second factor completed outside the window does not satisfy a user with a factor`() {
        val allowed = stepUpAllows(session(now.minusSeconds(301), mfa = true), hasFactor = true, enrolment = false, now = now, window = window)

        assertThat(allowed).isFalse()
    }

    @Test
    fun `a user without a factor needs no step-up for a sensitive action other than the first enrolment`() {
        val allowed = stepUpAllows(session(now.minusSeconds(3_600), mfa = false), hasFactor = false, enrolment = false, now = now, window = window)

        assertThat(allowed).describedAs("a password change of a user without a factor").isTrue()
    }

    @Test
    fun `a user without a factor cannot enrol a first factor with a stale password entry`() {
        val allowed = stepUpAllows(session(now.minusSeconds(301), mfa = false), hasFactor = false, enrolment = true, now = now, window = window)

        assertThat(allowed).isFalse()
    }

    @Test
    fun `a user without a factor enrols a first factor with a recent password entry`() {
        val allowed = stepUpAllows(session(now.minusSeconds(299), mfa = false), hasFactor = false, enrolment = true, now = now, window = window)

        assertThat(allowed).isTrue()
    }

    @Test
    fun `a caller that is not a browser session is never asked for a step-up`() {
        val allowed = stepUpAllows(null, hasFactor = true, enrolment = true, now = now, window = window)

        assertThat(allowed).describedAs("a Basic or Bearer caller keeps working without a prompt").isTrue()
    }

}
