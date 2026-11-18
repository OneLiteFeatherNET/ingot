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

@file:OptIn(ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.identity.login.challenge

import net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The registration of login challenge providers and the reading of the `challenge` member of a login request. */
internal class LoginChallengesTest {

    private val warnings = mutableListOf<String>()
    private val challenges = LoginChallenges(warn = { warnings += it })

    /** A provider that implements only its id, as a plugin built against the first version of the interface may. */
    private class IdOnly(override val id: String) : LoginChallengeProvider

    @Test
    fun `no provider is registered by default`() {
        assertThat(challenges.registered()).isNull()
    }

    @Test
    fun `the first registered provider is used and a later one is ignored with a warning`() {
        val first = IdOnly("first")
        LoginChallengeRegistrationEvent(challenges::register).register(first)
        LoginChallengeRegistrationEvent(challenges::register).register(IdOnly("second"))

        assertThat(challenges.registered()).describedAs("the first provider by load order is used").isSameAs(first)
        assertThat(warnings).describedAs("the ignored provider is named in one warning").singleElement().satisfies({
            assertThat(it).contains("second", "first")
        })
    }

    @Test
    fun `a provider with a blank id is refused with a warning`() {
        challenges.register(IdOnly(" "))

        assertThat(challenges.registered()).isNull()
        assertThat(warnings).hasSize(1)
    }

    @Test
    fun `a provider that implements only its id asks with empty parameters and verifies nothing`() {
        val provider = IdOnly("minimal")
        val context = LoginChallengeContext(username = "alice", address = "10.0.0.9")

        assertThat(provider.parameters(context)).isEmpty()
        assertThat(provider.verify(context, mapOf("nonce" to "1"))).isFalse()
    }

    @Test
    fun `a missing or null challenge is no challenge`() {
        assertThat(LoginChallengeAnswer.of(null)).isNull()
    }

    @Test
    fun `an object with a string provider and an object answer is a given challenge`() {
        val parsed = LoginChallengeAnswer.of(mapOf("provider" to "example-pow", "answer" to mapOf("nonce" to "42")))

        assertThat(parsed).isEqualTo(LoginChallengeAnswer.Given(provider = "example-pow", answer = mapOf("nonce" to "42")))
    }

    @Test
    fun `a challenge that is not an object is malformed`() {
        assertThat(LoginChallengeAnswer.of("example-pow")).isEqualTo(LoginChallengeAnswer.Malformed)
        assertThat(LoginChallengeAnswer.of(listOf("example-pow"))).isEqualTo(LoginChallengeAnswer.Malformed)
    }

    @Test
    fun `a challenge without a string provider is malformed`() {
        assertThat(LoginChallengeAnswer.of(mapOf("answer" to mapOf<String, Any?>()))).isEqualTo(LoginChallengeAnswer.Malformed)
        assertThat(LoginChallengeAnswer.of(mapOf("provider" to 7, "answer" to mapOf<String, Any?>()))).isEqualTo(LoginChallengeAnswer.Malformed)
    }

    @Test
    fun `a challenge without an object answer is malformed`() {
        assertThat(LoginChallengeAnswer.of(mapOf("provider" to "example-pow"))).isEqualTo(LoginChallengeAnswer.Malformed)
        assertThat(LoginChallengeAnswer.of(mapOf("provider" to "example-pow", "answer" to "42"))).isEqualTo(LoginChallengeAnswer.Malformed)
    }

}
