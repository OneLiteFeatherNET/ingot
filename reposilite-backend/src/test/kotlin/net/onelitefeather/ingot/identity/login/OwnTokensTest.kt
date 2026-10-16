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

package net.onelitefeather.ingot.identity.login

import com.reposilite.audit.api.AuditAction
import com.reposilite.audit.api.AuditActor
import com.reposilite.audit.api.AuditEvent
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.token.AccessTokenSecurityProvider
import net.onelitefeather.ingot.identity.login.api.SelfTokenRequest
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.Deleted
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.Invalid
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.Issued
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.NameTaken
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.NotFound
import net.onelitefeather.ingot.identity.login.OwnTokenOutcome.Rotated
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

internal class OwnTokensTest {

    private val clock = MutableClock(Instant.parse("2026-10-15T10:00:00Z"))
    private val actor = AuditActor.named("alice", "10.0.0.1")

    @TempDir
    lateinit var workingDirectory: Path

    private class Fixture(
        val identities: IdentityStore,
        val tokens: OwnTokens,
        val changed: MutableList<String>,
        val audit: MutableList<AuditEvent>,
    )

    private fun withFixture(block: (Fixture) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val identities = IdentityStore(connection.database, clock)
            val changed = mutableListOf<String>()
            val audit = mutableListOf<AuditEvent>()
            val tokens = OwnTokens(identities, onTokenChanged = changed::add, audit = audit::add, clock = clock)
            block(Fixture(identities, tokens, changed, audit))
        }
    }

    private fun Fixture.alice() =
        identities.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = clock.instant())!!

    @Test
    fun `issues a token whose secret is verified by the stored hash`() {
        withFixture { fixture ->
            val alice = fixture.alice()

            val outcome = fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci"))

            assertThat(outcome).isInstanceOf(Issued::class.java)
            val secret = (outcome as Issued).dto.secret
            val stored = fixture.identities.findToken("ci")!!
            assertThat(secret).describedAs("the secret must carry the token id").startsWith("ingot_${stored.id}_")
            assertThat(AccessTokenSecurityProvider.matches(stored.secretHash, secret))
                .describedAs("the stored hash must verify the returned secret")
                .isTrue()
        }
    }

    @Test
    fun `keeps the secret out of the token list`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            val secret = (fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci")) as Issued).dto.secret

            val listed = fixture.tokens.list(alice.id)

            assertThat(listed.map { it.name }).containsExactly("ci")
            assertThat(listed.toString()).describedAs("the list must never show a secret").doesNotContain(secret)
        }
    }

    @Test
    fun `refuses a second token with the same name`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci"))

            assertThat(fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci"))).isEqualTo(NameTaken)
        }
    }

    @Test
    fun `refuses a token name with a colon, which the basic credentials cannot carry`() {
        withFixture { fixture ->
            val outcome = fixture.tokens.issue(fixture.alice().id, actor, SelfTokenRequest(name = "ci:prod"))

            assertThat(outcome).isInstanceOf(Invalid::class.java)
        }
    }

    @Test
    fun `refuses an empty list of scopes`() {
        withFixture { fixture ->
            val outcome = fixture.tokens.issue(fixture.alice().id, actor, SelfTokenRequest(name = "ci", scopes = emptyList()))

            assertThat(outcome).describedAs("an empty scope list would deny everything, so it is refused").isInstanceOf(Invalid::class.java)
        }
    }

    @Test
    fun `refuses an unknown scope`() {
        withFixture { fixture ->
            val outcome = fixture.tokens.issue(fixture.alice().id, actor, SelfTokenRequest(name = "ci", scopes = listOf("ROOT")))

            assertThat(outcome).isInstanceOf(Invalid::class.java)
        }
    }

    @Test
    fun `stores the scopes it was given`() {
        withFixture { fixture ->
            val alice = fixture.alice()

            fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "reader", scopes = listOf("READ")))

            assertThat(fixture.identities.findToken("reader")?.scopes).isEqualTo(setOf(Action.READ))
        }
    }

    @Test
    fun `rotates the secret and the old secret no longer verifies`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            val old = (fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci")) as Issued).dto.secret

            val rotated = fixture.tokens.rotate(alice.id, "ci", actor)

            assertThat(rotated).isInstanceOf(Rotated::class.java)
            val fresh = (rotated as Rotated).dto.secret
            val stored = fixture.identities.findToken("ci")!!
            assertThat(fresh).isNotEqualTo(old)
            assertThat(AccessTokenSecurityProvider.matches(stored.secretHash, old)).describedAs("the old secret must stop working").isFalse()
            assertThat(AccessTokenSecurityProvider.matches(stored.secretHash, fresh)).describedAs("the new secret must work").isTrue()
        }
    }

    @Test
    fun `reports a rotated token as changed so that cached credentials are dropped`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci"))

            fixture.tokens.rotate(alice.id, "ci", actor)

            assertThat(fixture.changed).describedAs("the authentication cache must hear about the change").contains("ci")
        }
    }

    @Test
    fun `deletes a token and reports the change`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            fixture.tokens.issue(alice.id, actor, SelfTokenRequest(name = "ci"))

            val outcome = fixture.tokens.delete(alice.id, "ci", actor)

            assertThat(outcome).isEqualTo(Deleted)
            assertThat(fixture.identities.findToken("ci")).describedAs("the token row must be gone").isNull()
            assertThat(fixture.changed).contains("ci")
        }
    }

    @Test
    fun `refuses to rotate or delete a token of another principal`() {
        withFixture { fixture ->
            val alice = fixture.alice()
            val bob = fixture.identities.createPrincipal(PrincipalKind.USER, "bob", "Bob", disabled = false, createdAt = clock.instant())!!
            fixture.tokens.issue(bob.id, actor, SelfTokenRequest(name = "bobs"))

            assertThat(fixture.tokens.rotate(alice.id, "bobs", actor)).describedAs("another user's token is not found for alice").isEqualTo(NotFound)
            assertThat(fixture.tokens.delete(alice.id, "bobs", actor)).isEqualTo(NotFound)
            assertThat(fixture.identities.findToken("bobs")).describedAs("bob's token must survive").isNotNull
        }
    }

    @Test
    fun `names the token behind a bearer secret`() {
        withFixture { fixture ->
            val secret = (fixture.tokens.issue(fixture.alice().id, actor, SelfTokenRequest(name = "ci")) as Issued).dto.secret

            assertThat(fixture.tokens.nameOfBearer(secret)).describedAs("the id in the secret leads to the token name").isEqualTo("ci")
            assertThat(fixture.tokens.nameOfBearer("ingot_9999_abcdef")).describedAs("an unknown id names nothing").isNull()
            assertThat(fixture.tokens.nameOfBearer("garbage")).isNull()
        }
    }

    @Test
    fun `audits a created token without its secret`() {
        withFixture { fixture ->
            val secret = (fixture.tokens.issue(fixture.alice().id, actor, SelfTokenRequest(name = "ci")) as Issued).dto.secret

            val event = fixture.audit.single()
            assertThat(event.action).isEqualTo(AuditAction.TOKEN_CREATED)
            assertThat(event.toString()).describedAs("the audit record must never contain the secret").doesNotContain(secret)
        }
    }

}
