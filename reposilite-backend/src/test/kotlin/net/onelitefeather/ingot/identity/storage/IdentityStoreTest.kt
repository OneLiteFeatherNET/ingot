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
package net.onelitefeather.ingot.identity.storage

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import net.onelitefeather.ingot.identity.login.storage.LoginStore
import net.onelitefeather.ingot.identity.evaluation.PermissionEvaluator
import net.onelitefeather.ingot.identity.model.Action
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.model.Role
import net.onelitefeather.ingot.identity.model.TeamMemberSource
import net.onelitefeather.ingot.identity.model.privateTeamName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class IdentityStoreTest {

    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val createdAt: Instant = Instant.parse("2026-10-14T08:00:00Z")

    @TempDir
    lateinit var workingDirectory: Path

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `creates a principal that can be found in any letter case`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a machine principal
            val created = store.createPrincipal(PrincipalKind.MACHINE, "CI-Bot", "CI-Bot", disabled = false, createdAt = createdAt)

            // then: it is found by its name in any letter case, and keeps the spelling it was created with
            assertThat(created).describedAs("principal should be created").isNotNull
            assertThat(store.findPrincipal("ci-bot")?.name).describedAs("lookup should ignore letter case").isEqualTo("CI-Bot")
            assertThat(store.findPrincipal("CI-BOT")?.id).isEqualTo(created?.id)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `refuses a principal whose name differs only in case`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: an existing principal
            store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = createdAt)

            // when: another principal is created with the same name in other case
            val duplicate = store.createPrincipal(PrincipalKind.MACHINE, "ALICE", "Alice", disabled = false, createdAt = createdAt)

            // then: the duplicate is refused and only one principal exists
            assertThat(duplicate).describedAs("a case-only duplicate must be refused").isNull()
            assertThat(store.listPrincipals()).describedAs("exactly one principal should exist").hasSize(1)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `refuses a team whose name differs only in case`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: an existing team
            store.createTeam("Deployers", "", externalRef = null)

            // when / then: a team with the same name in other case is refused
            assertThat(store.createTeam("deployers", "", externalRef = null)).describedAs("a case-only duplicate team must be refused").isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `replacing manual members keeps synchronised members`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a team with one synchronised and one manual member
            val team = store.createTeam("mods", "", null)!!
            val alice = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, createdAt)!!
            val bob = store.createPrincipal(PrincipalKind.USER, "bob", "Bob", false, createdAt)!!
            val carol = store.createPrincipal(PrincipalKind.USER, "carol", "Carol", false, createdAt)!!
            store.addMember(team.id, alice.id, TeamMemberSource.SYNC)
            store.addMember(team.id, bob.id, TeamMemberSource.MANUAL)

            // when: the manual members are replaced with carol only
            store.replaceManualMembers(team.id, setOf(carol.id))

            // then: alice keeps her synchronised membership, bob is gone and carol is a manual member
            val members = store.listMemberships(team.id).associate { it.principalId to it.source }
            assertThat(members).describedAs("memberships after replacing manual members")
                .containsExactlyInAnyOrderEntriesOf(mapOf(alice.id to TeamMemberSource.SYNC, carol.id to TeamMemberSource.MANUAL))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `a token keeps its scopes and expiry on a round trip`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a machine principal and a narrowed token that expires in an hour
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "bot", "bot", false, createdAt)!!
            val expiresAt = clock.instant().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS)

            // when: the token is created with the scopes READ and WRITE
            store.createToken(bot.id, "bot-token", "hash", setOf(Action.WRITE, Action.READ), expiresAt, LocalDate.of(2026, 10, 14), "ci")

            // then: scopes, expiry and description come back unchanged
            val token = store.findToken("bot-token")!!
            assertThat(token.scopes).describedAs("scopes should round-trip").isEqualTo(setOf(Action.READ, Action.WRITE))
            assertThat(token.expiresAt).describedAs("expiry should round-trip").isEqualTo(expiresAt)
            assertThat(token.description).isEqualTo("ci")
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `a token without scopes is stored as unrestricted`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a machine principal
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "bot", "bot", false, createdAt)!!

            // when: a token is created without scopes
            store.createToken(bot.id, "free", "hash", scopes = null, expiresAt = null, createdAt = LocalDate.of(2026, 10, 14), description = "")

            // then: it reads back as unrestricted, which is what legacy tokens are
            assertThat(store.findToken("free")?.scopes).describedAs("a token without scopes is unrestricted").isNull()
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `refuses a second token with the same name`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a token named "deploy"
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "bot", "bot", false, createdAt)!!
            store.createToken(bot.id, "deploy", "hash", null, null, LocalDate.of(2026, 10, 14), "")

            // when: a second token with the same name is created
            val duplicate = store.createToken(bot.id, "deploy", "other", null, null, LocalDate.of(2026, 10, 14), "")

            // then: the duplicate is refused; token names are exact, as they were for access tokens
            assertThat(duplicate).describedAs("duplicate token name must be refused").isNull()
            assertThat(store.listTokens()).hasSize(1)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `an instance role held through a team makes its members instance admins`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a team holding the instance role and a member of it
            val admins = store.createTeam("admins", "", null)!!
            val alice = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", false, createdAt)!!
            val bob = store.createPrincipal(PrincipalKind.USER, "bob", "Bob", false, createdAt)!!
            store.addMember(admins.id, alice.id, TeamMemberSource.MANUAL)
            store.grantInstanceRole(principalId = null, teamId = admins.id)

            // when / then: the member is an instance admin, the non-member is not
            assertThat(PermissionEvaluator.isInstanceAdmin(store.loadAccess(alice, scopes = null))).describedAs("team member should inherit the instance role").isTrue
            assertThat(PermissionEvaluator.isInstanceAdmin(store.loadAccess(bob, scopes = null))).describedAs("non-member should not be an instance admin").isFalse
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `loads the grants of every team of a principal and of no other`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: two teams with grants, and a principal that is a member of only one of them
            val deployers = store.createTeam("deployers", "", null)!!
            val readers = store.createTeam("readers", "", null)!!
            val ci = store.createPrincipal(PrincipalKind.MACHINE, "ci", "ci", false, createdAt)!!
            store.addMember(deployers.id, ci.id, TeamMemberSource.MANUAL)
            store.addGrant(deployers.id, "releases", "com/example", Role.DEPLOY)
            store.addGrant(readers.id, "snapshots", null, Role.READ)

            // when: the access of the principal is loaded
            val access = store.loadAccess(ci, scopes = null)

            // then: only the grant of the team it belongs to is part of it
            assertThat(access.levels.flatMap { it.grants }).describedAs("grants of the principal's teams")
                .containsExactly(net.onelitefeather.ingot.identity.model.Grant("deployers", Role.DEPLOY, "releases", "com/example"))
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `deleting a principal removes its tokens, memberships, instance role and private team`(databaseConfiguration: String) {
        withStore(databaseConfiguration) { store ->
            // given: a machine principal with a token, a membership, an instance role and a private team with a legacy grant
            val bot = store.createPrincipal(PrincipalKind.MACHINE, "bot", "bot", false, createdAt)!!
            val team = store.createTeam("mods", "", null)!!
            val privateTeam = store.createTeam(privateTeamName("bot"), "private", null)!!
            store.addMember(privateTeam.id, bot.id, TeamMemberSource.MANUAL)
            store.addMember(team.id, bot.id, TeamMemberSource.MANUAL)
            store.addGrant(privateTeam.id, null, "/releases", Role.LEGACY_READ)
            store.grantInstanceRole(principalId = bot.id, teamId = null)
            store.createToken(bot.id, "bot", "hash", null, null, LocalDate.of(2026, 10, 14), "")

            // when: the principal is deleted
            store.deletePrincipal(bot)

            // then: everything that belonged to it is gone and the unrelated team is kept
            assertThat(store.findPrincipal("bot")).describedAs("principal should be deleted").isNull()
            assertThat(store.findToken("bot")).describedAs("its token should be deleted").isNull()
            assertThat(store.findTeam(privateTeamName("bot"))).describedAs("its private team should be deleted").isNull()
            assertThat(store.hasDirectInstanceRole(bot.id)).describedAs("its instance role should be deleted").isFalse
            assertThat(store.listMemberships(team.id)).describedAs("memberships of other teams should be gone").isEmpty()
            assertThat(store.findTeam("mods")).describedAs("unrelated team should be kept").isNotNull
        }
    }

    @Test
    fun `deleting a principal removes its login credential and refresh tokens without relying on foreign keys`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            // given: a user with a password and a browser session
            val store = IdentityStore(connection.database, clock)
            val logins = LoginStore(connection.database, clock)
            val alice = store.createPrincipal(PrincipalKind.USER, "alice", "Alice", disabled = false, createdAt = clock.instant())!!
            logins.saveCredential(alice.id, "argon2id-hash", mustChangePassword = false)
            logins.insertRefreshToken(
                familyId = "family-of-alice",
                principalId = alice.id,
                tokenHash = "hash-of-alice-refresh",
                createdAt = clock.instant(),
                expiresAt = clock.instant().plusSeconds(3600),
                userAgent = null,
                ip = null,
            )
            // and: a connection that does not enforce foreign keys, so the cascades of the schema cannot do the removal
            connection.databaseSource.connection.use { raw -> raw.createStatement().use { it.execute("PRAGMA foreign_keys=OFF") } }

            // when: the principal is deleted
            store.deletePrincipal(alice)

            // then: nothing that could log in or renew a session is left behind
            assertThat(logins.findCredential(alice.id)).describedAs("the password of a deleted principal must be removed").isNull()
            assertThat(logins.findRefreshToken("hash-of-alice-refresh")).describedAs("a deleted principal's refresh token must be removed").isNull()
        }
    }

    private fun withStore(databaseConfiguration: String, block: (IdentityStore) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, databaseConfiguration, 1).use { connection ->
            block(IdentityStore(connection.database, clock))
        }
    }

}
