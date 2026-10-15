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

import net.onelitefeather.ingot.identity.login.MutableClock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.Base64

internal class SessionSignerTest {

    private val now = Instant.parse("2026-10-15T10:00:00Z")
    private val clock = MutableClock(now)
    private val key = ByteArray(32) { it.toByte() }
    private val signer = SessionSigner(key, clock)
    private val expiresAt = now.plus(Duration.ofMinutes(15))

    private fun issue(signer: SessionSigner = this.signer): String =
        signer.issue(principalId = 7, name = "alice", kind = "USER", familyId = "family-1", expiresAt = expiresAt)

    @Test
    fun `verifies a session it issued and returns the claims`() {
        val verified = signer.verify(issue())

        assertThat(verified).describedAs("a freshly issued session must verify").isNotNull
        assertThat(verified?.principalId).isEqualTo(7)
        assertThat(verified?.name).isEqualTo("alice")
        assertThat(verified?.kind).isEqualTo("USER")
        assertThat(verified?.familyId).isEqualTo("family-1")
        assertThat(verified?.expiresAt).isEqualTo(expiresAt)
    }

    @Test
    fun `accepts a session one second before its expiry`() {
        val token = issue()
        clock.advance(Duration.ofMinutes(15).minusSeconds(1))

        assertThat(signer.verify(token)).describedAs("a session is valid until its expiry instant").isNotNull
    }

    @Test
    fun `rejects a session at the instant of its expiry`() {
        val token = issue()
        clock.advance(Duration.ofMinutes(15))

        assertThat(signer.verify(token)).describedAs("at the expiry instant the session is already over").isNull()
    }

    @Test
    fun `rejects a session whose payload was changed`() {
        val parts = issue().split('.')
        val forgedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"1","name":"root","kind":"USER","sid":"family-1","exp":4102444800}""".toByteArray())

        assertThat(signer.verify("${parts[0]}.$forgedPayload.${parts[2]}"))
            .describedAs("a session with a changed payload must not verify, so a user cannot raise their own id")
            .isNull()
    }

    @Test
    fun `rejects a session signed with another key`() {
        val other = SessionSigner(ByteArray(32) { 9 }, clock)

        assertThat(signer.verify(issue(other))).describedAs("instances without the shared key must not accept the session").isNull()
    }

    @Test
    fun `rejects an unsigned session`() {
        val header = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"none"}""".toByteArray())
        val payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"sub":"7","name":"alice","kind":"USER","sid":"f","exp":4102444800}""".toByteArray())

        assertThat(signer.verify("$header.$payload.")).describedAs("the algorithm none must never be accepted").isNull()
    }

    @Test
    fun `rejects a value that is not a session at all`() {
        assertThat(signer.verify("not-a-jwt")).describedAs("garbage must fail verification, not throw").isNull()
    }

}
