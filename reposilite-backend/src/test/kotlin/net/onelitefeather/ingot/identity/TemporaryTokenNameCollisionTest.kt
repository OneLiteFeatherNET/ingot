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
package net.onelitefeather.ingot.identity

import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenType.PERSISTENT
import com.reposilite.token.AccessTokenType.TEMPORARY
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.api.CreateAccessTokenRequest
import com.reposilite.token.application.AccessTokenComponents
import net.onelitefeather.ingot.identity.model.privateTeamName
import net.onelitefeather.ingot.identity.storage.IdentityStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Pins today's `--token name:secret` behaviour. The startup bootstrap creates the token as TEMPORARY, and a persistent
 * token with the same name is deleted: its secret, its rights and its legacy grants go with it. The temporary token
 * then wins for the rest of the process. The bootstrap is repeated here as the plugin runs it.
 */
internal class TemporaryTokenNameCollisionTest {

    private val journalist = InMemoryLogger()

    @TempDir
    lateinit var workingDirectory: Path

    @Test
    fun `a temporary token replaces a persistent token of the same name`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val facade = AccessTokenComponents(journalist, database).accessTokenFacade()

            // given: a persistent manager token named "shared" with a route
            val persistent = facade.createAccessToken(
                CreateAccessTokenRequest(
                    type = PERSISTENT,
                    name = "shared",
                    secret = "persistent-secret",
                    permissions = setOf(MANAGER),
                    routes = setOf(CreateAccessTokenRequest.Route("/releases", setOf(READ))),
                ),
                audited = true,
            )
            assertThat(persistent.accessToken.identifier.type).isEqualTo(PERSISTENT)

            // when: the --token bootstrap runs with the same name
            val temporary = facade.createAccessToken(
                CreateAccessTokenRequest(type = TEMPORARY, name = "shared", secret = "temporary-secret"),
                audited = false,
            ).accessToken
            facade.addPermission(temporary.identifier, MANAGER, audited = false)

            // then: the persistent token is gone, the temporary one answers under the name
            assertThat(facade.getAccessToken("shared")?.identifier?.type).describedAs("token answering to the name").isEqualTo(TEMPORARY)
            assertThat(facade.count()).describedAs("number of tokens").isEqualTo(1L)
            assertThat(facade.secretMatches(temporary.identifier, "temporary-secret")).describedAs("temporary secret").isTrue
            assertThat(IdentityStore(database).findToken("shared")).describedAs("persistent token in the identity tables").isNull()
        }
    }

    @Test
    fun `the persistent rights of the replaced token do not survive the replacement`() {
        DatabaseConnectionFactory.createConnection(workingDirectory, "sqlite --temporary", 1).use { connection ->
            val database = connection.database
            val facade = AccessTokenComponents(journalist, database).accessTokenFacade()

            // given: a persistent manager token named "shared" with a route
            facade.createAccessToken(
                CreateAccessTokenRequest(
                    type = PERSISTENT,
                    name = "shared",
                    secret = "persistent-secret",
                    permissions = setOf(MANAGER),
                    routes = setOf(CreateAccessTokenRequest.Route("/releases", setOf(READ))),
                ),
                audited = true,
            )

            // when: the temporary token with the same name replaces it
            val temporary = facade.createAccessToken(
                CreateAccessTokenRequest(type = TEMPORARY, name = "shared", secret = "temporary-secret"),
                audited = false,
            ).accessToken

            // then: the temporary token holds no rights of its own, and the legacy grants of the old one are gone
            assertThat(facade.hasPermission(temporary.identifier, MANAGER)).describedAs("manager rights of the temporary token").isFalse
            assertThat(facade.hasPermissionTo(temporary.identifier, "/releases/x.jar", READ)).describedAs("route of the replaced token").isFalse
            val store = IdentityStore(database)
            val machine = store.findPrincipal("shared")
            assertThat(machine).describedAs("machine principal kept as an empty shell").isNotNull
            assertThat(store.listGrants(store.findTeam(privateTeamName("shared"))!!.id)).describedAs("legacy grants of the replaced token").isEmpty()
            assertThat(store.hasDirectInstanceRole(machine!!.id)).describedAs("instance role of the replaced token").isFalse
        }
    }

}
