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

package com.reposilite.status

import com.reposilite.ReposiliteObjectMapper
import com.reposilite.auth.SignInObserver
import com.reposilite.auth.SignInOutcome
import com.reposilite.auth.SignInPath
import com.reposilite.journalist.backend.InMemoryLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

/**
 * The sign-in counters in the instance status: absent while the account throttle switch is off, and per path and outcome
 * while it is on, with no name or address in the answer.
 */
internal class SignInStatusTest {

    private fun status(observer: SignInObserver) =
        StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = FailureFacade(InMemoryLogger()),
            signInObserver = { observer },
        )

    private fun observer(enabled: Boolean) =
        SignInObserver(enabled = enabled, audit = {}, publish = {}, warn = {})

    @Test
    fun `the instance status has no sign-in field while the switch is off`() {
        val response = status(observer(enabled = false)).fetchInstanceStatus()

        assertThat(response.signIn).isNull()
        assertThat(ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(response)).doesNotContain("signIn")
    }

    @Test
    fun `the instance status counts each path and outcome while the switch is on`() {
        val observer = observer(enabled = true)
        observer.record(SignInPath.DASHBOARD, SignInOutcome.SUCCEEDED)
        observer.record(SignInPath.BASIC, SignInOutcome.FAILED)
        observer.record(SignInPath.BASIC, SignInOutcome.THROTTLED)
        observer.record(SignInPath.BASIC, SignInOutcome.THROTTLED)

        val signIn = status(observer).fetchInstanceStatus().signIn

        assertThat(signIn).isNotNull
        assertThat(signIn!!.dashboard.succeeded).isEqualTo(1)
        assertThat(signIn.basic.failed).isEqualTo(1)
        assertThat(signIn.basic.throttled).isEqualTo(2)
        assertThat(signIn.bearer.throttled).isZero()
        assertThat(signIn.mfa.succeeded).isZero()
    }

    @Test
    fun `the sign-in counters carry no name and no address`() {
        val observer = observer(enabled = true)
        observer.throttled(name = "deploy-bot", address = "203.0.113.7", path = SignInPath.BEARER, retryAfterSeconds = 9)

        val json = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.writeValueAsString(status(observer).fetchInstanceStatus())

        assertThat(json).contains("\"bearer\"").doesNotContain("deploy-bot", "203.0.113.7")
    }

}
