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

package com.reposilite.auth

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditEvent
import com.reposilite.plugin.api.Event
import com.reposilite.plugin.api.LoginThrottledEvent
import com.reposilite.plugin.api.NewSignInEvent
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test

internal class SignInObserverTest {

    private val audited = mutableListOf<AuditEvent>()
    private val published = mutableListOf<Event>()
    private val warnings = mutableListOf<String>()

    private fun observer(enabled: Boolean, publish: (Event) -> Unit = { published += it }) =
        SignInObserver(enabled = enabled, audit = { audited += it }, publish = publish, warn = { warnings += it })

    @Test
    fun `a throttled request is counted under its path and outcome only`() {
        val observer = observer(enabled = true)

        observer.throttled(name = "deploy", address = "203.0.113.7", path = SignInPath.BASIC, retryAfterSeconds = 4)

        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.THROTTLED)).isEqualTo(1)
        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.FAILED)).isZero()
        assertThat(observer.counters.count(SignInPath.BEARER, SignInOutcome.THROTTLED)).isZero()
    }

    @Test
    fun `a throttled request writes one LOGIN_THROTTLED with its path and no secret`() {
        val observer = observer(enabled = true)

        observer.throttled(name = "deploy", address = "203.0.113.7", path = SignInPath.BASIC, retryAfterSeconds = 4)

        val event = audited.single()
        assertThat(event.action).isEqualTo(AuditAction.LOGIN_THROTTLED)
        assertThat(event.success).isFalse()
        assertThat(event.details).containsExactly(entry("path", "basic"))
    }

    @Test
    fun `a throttled request publishes LoginThrottledEvent with the remaining delay`() {
        val observer = observer(enabled = true)

        observer.throttled(name = "deploy", address = "203.0.113.7", path = SignInPath.BASIC, retryAfterSeconds = 4)

        assertThat(published).containsExactly(LoginThrottledEvent(name = "deploy", address = "203.0.113.7", path = "basic", retryAfterSeconds = 4))
    }

    @Test
    fun `a listener that throws is logged and does not reach the caller`() {
        val observer = observer(enabled = true, publish = { error("listener failed") })

        assertThatCode {
            observer.throttled(name = "deploy", address = "203.0.113.7", path = SignInPath.BEARER, retryAfterSeconds = 1)
        }.doesNotThrowAnyException()
        assertThat(warnings).hasSize(1)
    }

    @Test
    fun `a new sign-in publishes NewSignInEvent carrying a hash of the user agent and never the raw value`() {
        val observer = observer(enabled = true)
        val userAgent = "Gradle/8.14 (Linux; amd64)"

        observer.newSignIn(name = "alice", address = "10.0.0.9", path = SignInPath.DASHBOARD, userAgent = userAgent)

        val event = published.single() as NewSignInEvent
        assertThat(event.name).isEqualTo("alice")
        assertThat(event.address).isEqualTo("10.0.0.9")
        assertThat(event.path).isEqualTo("dashboard")
        assertThat(event.userAgentHash).hasSize(64).doesNotContain(userAgent)
    }

    @Test
    fun `a new sign-in without a user agent carries no hash`() {
        val observer = observer(enabled = true)

        observer.newSignIn(name = "ci", address = "10.0.0.9", path = SignInPath.BASIC, userAgent = null)

        assertThat((published.single() as NewSignInEvent).userAgentHash).isNull()
    }

    @Test
    fun `while the switch is off nothing is counted, audited or published`() {
        val observer = observer(enabled = false)

        observer.record(SignInPath.DASHBOARD, SignInOutcome.SUCCEEDED)
        observer.throttled(name = "deploy", address = "203.0.113.7", path = SignInPath.BASIC, retryAfterSeconds = 4)
        observer.newSignIn(name = "alice", address = "10.0.0.9", path = SignInPath.DASHBOARD, userAgent = "agent")

        assertThat(observer.counters.count(SignInPath.DASHBOARD, SignInOutcome.SUCCEEDED)).isZero()
        assertThat(observer.counters.count(SignInPath.BASIC, SignInOutcome.THROTTLED)).isZero()
        assertThat(audited).isEmpty()
        assertThat(published).isEmpty()
    }

}
