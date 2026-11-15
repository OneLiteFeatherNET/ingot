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

import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.shared.ErrorResponse
import com.reposilite.shared.unauthorizedError
import com.reposilite.token.api.AccessTokenDto
import com.reposilite.token.specification.AccessTokenSpecification
import net.onelitefeather.ingot.identity.model.PrincipalKind
import net.onelitefeather.ingot.identity.storage.AuthorizationRevision
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.asSuccess
import java.time.Instant

/**
 * The credential cache follows the policy revision: an entry cached under an older revision is verified again, so a
 * revocation made on another replica is refused after one refresh. The authenticator is a fake whose answer is switched
 * to simulate that revocation, and the identity writes go to a temporary SQLite file.
 */
internal class AuthenticationRevisionCacheTest : AccessTokenSpecification() {

    private val authenticator = RevocableAuthenticator()

    @Test
    fun `a cached credential is not verified again while the revision does not move`() {
        // given: a token that authenticates, and a facade whose cache holds it
        authenticator.accepted = createToken("ci", "secret")
        withRevision { _, revision ->
            val facade = authenticationFacade(revision)
            facade.authenticateByCredentials(Credentials("host", "ci", "secret"))

            // when: the same credentials are presented again without any identity write
            facade.authenticateByCredentials(Credentials("host", "ci", "secret"))

            // then: the authenticator ran once, the second answer came from the cache
            assertThat(authenticator.calls).describedAs("the second request is served from the cache").isEqualTo(1)
        }
    }

    @Test
    fun `a cached credential of an older revision is verified again after a refresh`() {
        // given: a token that authenticates, cached under the revision seen before the change
        authenticator.accepted = createToken("ci", "secret")
        withRevision { store, revision ->
            val facade = authenticationFacade(revision)
            assertThat(facade.authenticateByCredentials(Credentials("host", "ci", "secret")).isOk).isTrue()

            // and: the token is revoked on another replica, which advances the revision and is seen by one refresh
            authenticator.accepted = null
            store.createPrincipal(PrincipalKind.USER, "other", "Other", disabled = false, createdAt = Instant.EPOCH)
            revision.refreshOnce()

            // when: the credentials are presented again
            val response = facade.authenticateByCredentials(Credentials("host", "ci", "secret"))

            // then: the old entry was dropped, the credentials were verified again and refused
            assertThat(response.isErr).describedAs("the revoked credentials are refused after one refresh").isTrue()
            assertThat(authenticator.calls).describedAs("the credentials were verified a second time").isEqualTo(2)
        }
    }

    private fun authenticationFacade(revision: AuthorizationRevision): AuthenticationFacade =
        AuthenticationFacade(
            journalist = logger,
            authenticators = sortedSetOf(Authenticator.priorityComparator, authenticator),
            accessTokenFacade = accessTokenFacade,
            authorizationRevision = revision,
        )

    private fun withRevision(block: (IdentityStore, AuthorizationRevision) -> Unit) {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val store = IdentityStore(connection.database)
            block(store, AuthorizationRevision(store))
        }
    }

    private class RevocableAuthenticator : Authenticator {
        var accepted: AccessTokenDto? = null
        var calls = 0

        override fun authenticate(credentials: Credentials): Result<AccessTokenDto, ErrorResponse> {
            calls++
            return accepted?.asSuccess() ?: unauthorizedError("Invalid authorization credentials")
        }

        override fun enabled(): Boolean = true

        override fun priority(): Double = 0.0

        override fun realm(): String = "revocable"
    }
}
