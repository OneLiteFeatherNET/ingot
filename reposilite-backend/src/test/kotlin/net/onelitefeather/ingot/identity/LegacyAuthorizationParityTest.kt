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

import com.reposilite.auth.BasicAuthenticator
import com.reposilite.auth.api.Credentials
import com.reposilite.configuration.local.infrastructure.DatabaseConnectionFactory
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.token.AccessToken
import com.reposilite.token.AccessTokenFacade
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenPermission.MANAGER
import com.reposilite.token.AccessTokenSecurityProvider
import com.reposilite.token.Route
import com.reposilite.token.RoutePermission
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.RoutePermission.WRITE
import com.reposilite.token.application.AccessTokenComponents
import com.reposilite.token.infrastructure.SqlAccessTokenRepository
import net.onelitefeather.ingot.identity.model.Action
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path
import java.time.LocalDate

/**
 * Legacy tokens written the way an old installation writes them, migrated, and then checked against the old rules: the
 * legacy routes and the legacy canSee expression, applied to the rows as they were before the migration. Authentication
 * goes through the real BasicAuthenticator.
 */
internal class LegacyAuthorizationParityTest {

    private val journalist = InMemoryLogger()

    @TempDir
    lateinit var workingDirectory: Path

    private data class LegacyView(
        val identifier: AccessTokenIdentifier,
        val secret: String,
        val manager: Boolean,
        val routes: Set<Route>,
    )

    private val paths = listOf(
        "/releases",
        "/releases/com/example/app/1.0/app-1.0.jar",
        "/releases/com/examples/x.jar",
        "/RELEASES/com/example/x.jar",
        "/releases/com/example",
        "/releasesX/y.jar",
        "/snapshots/com/example/a.jar",
        "/snapshots/other/b.jar",
        "/",
        "/unknown/path/x.jar",
    )

    private val fragments = listOf("/releases", "/RELEASES", "/releasesX", "/rel", "/snapshots", "/releases/com/example/app", "/unknown")

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `migrated tokens authorize every path and action as the legacy route check did`(databaseConfiguration: String) {
        withSeededDatabase(databaseConfiguration) { views, facade ->
            for ((name, view) in views) {
                for (path in paths) {
                    // reads and writes check the route permission directly, deletes check the write permission
                    for (permission in listOf(READ, WRITE)) {
                        assertThat(facade.hasPermissionTo(view.identifier, path, permission))
                            .describedAs("token '$name' with $permission on '$path'")
                            .isEqualTo(legacyHasPermissionTo(view, path, permission))
                    }
                    assertThat(facade.hasAccessTo(view.identifier, path, Action.READ))
                        .describedAs("token '$name' READ on '$path'")
                        .isEqualTo(legacyHasPermissionTo(view, path, READ))
                    assertThat(facade.hasAccessTo(view.identifier, path, Action.WRITE))
                        .describedAs("token '$name' WRITE on '$path'")
                        .isEqualTo(legacyHasPermissionTo(view, path, WRITE))
                    assertThat(facade.hasAccessTo(view.identifier, path, Action.DELETE))
                        .describedAs("token '$name' DELETE on '$path' (was WRITE)")
                        .isEqualTo(legacyHasPermissionTo(view, path, WRITE))
                }
                for (fragment in fragments) {
                    assertThat(facade.canSee(view.identifier, fragment))
                        .describedAs("token '$name' visibility of '$fragment'")
                        .isEqualTo(legacyCanSee(view, fragment))
                }
                assertThat(facade.hasPermission(view.identifier, MANAGER))
                    .describedAs("token '$name' manager")
                    .isEqualTo(view.manager)
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["sqlite --temporary", "h2 --temporary"])
    fun `migrated tokens authenticate with their secret and only with it`(databaseConfiguration: String) {
        withSeededDatabase(databaseConfiguration) { views, facade ->
            val authenticator = BasicAuthenticator(facade)
            for ((name, view) in views) {
                // the stored secret still matches, a wrong one does not
                assertThat(authenticator.authenticate(Credentials("127.0.0.1", name, view.secret)).isOk)
                    .describedAs("token '$name' with its secret")
                    .isTrue
                assertThat(authenticator.authenticate(Credentials("127.0.0.1", name, "wrong-secret")).isOk)
                    .describedAs("token '$name' with a wrong secret")
                    .isFalse
                // the authenticated identity is the migrated token, with its identifier
                assertThat(authenticator.authenticate(Credentials("127.0.0.1", name, view.secret)).orNull()?.identifier)
                    .describedAs("identifier of token '$name' after authentication")
                    .isEqualTo(view.identifier)
            }
        }
    }

    /** The old Route.hasPermissionTo of a token, over its legacy rights. */
    private fun legacyHasPermissionTo(view: LegacyView, path: String, permission: RoutePermission): Boolean =
        view.manager || view.routes.any { it.hasPermissionTo(path, permission) }

    /** The old AccessTokenFacade.canSee expression, over the legacy rights. */
    private fun legacyCanSee(view: LegacyView, fragment: String): Boolean =
        view.manager || view.routes.any { it.path.startsWith(fragment, ignoreCase = true) || fragment.startsWith(it.path) }

    private fun withSeededDatabase(
        databaseConfiguration: String,
        block: (Map<String, LegacyView>, AccessTokenFacade) -> Unit,
    ) {
        DatabaseConnectionFactory.createConnection(workingDirectory, databaseConfiguration, 1).use { connection ->
            val database = connection.database
            val legacy = SqlAccessTokenRepository(database, journalist, emptyArray())
            val views = seed(legacy)

            // the facade migrates on construction, after the legacy rows above were written
            val facade = AccessTokenComponents(journalist, database).accessTokenFacade()
            block(views, facade)
        }
    }

    private fun seed(legacy: SqlAccessTokenRepository): Map<String, LegacyView> {
        val specs = listOf(
            Spec("admin", manager = true, routes = emptyList()),
            Spec("ci-bot", manager = false, routes = listOf(Route("/releases", READ), Route("/snapshots/com/example", WRITE))),
            Spec("mirror", manager = false, routes = listOf(Route("/", READ), Route("/releases/com/example", WRITE))),
            Spec("reader", manager = false, routes = listOf(Route("/releases", READ))),
            Spec("writer", manager = false, routes = listOf(Route("/releases", WRITE), Route("/releases/com/example", READ))),
            Spec("plain", manager = false, routes = emptyList()),
        )
        return specs.associate { spec ->
            val secret = "secret-${spec.name}"
            val token = legacy.saveAccessToken(
                AccessToken(
                    name = spec.name,
                    encryptedSecret = AccessTokenSecurityProvider.encodeSecret(secret),
                    createdAt = LocalDate.of(2025, 3, 1),
                )
            )
            if (spec.manager) legacy.addPermission(token.identifier, MANAGER)
            spec.routes.forEach { legacy.addRoute(token.identifier, it) }
            spec.name to LegacyView(
                identifier = token.identifier,
                secret = secret,
                manager = spec.manager,
                routes = legacy.findAccessTokenRoutesById(token.identifier),
            )
        }
    }

    private data class Spec(val name: String, val manager: Boolean, val routes: List<Route>)

}
