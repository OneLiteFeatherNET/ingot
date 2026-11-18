/*
 * Copyright (c) 2023 dzikoysk
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

@file:OptIn(net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.access

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.api.RepositoryListQuery
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.api.AccessTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Parity of [ViewerAccessFacade] with the routes: every answer of a [Viewer] equals the answer the route function gives
 * for the same token and the same input, over a matrix of visibilities and viewers.
 */
internal class ViewerAccessParityTest : MavenSpecification() {

    private val gav = "com/example/app/1.0/app-1.0.jar"

    override fun repositories() = listOf(
        RepositorySettings("open", visibility = PUBLIC),
        RepositorySettings("internal", visibility = PRIVATE),
        RepositorySettings("shadow", visibility = HIDDEN),
    )

    private val repositoryNames = listOf("open", "internal", "shadow")

    /** Every viewer of the matrix, by a name that appears in the failure message. */
    private fun viewers(): Map<String, AccessTokenIdentifier?> = mapOf(
        "anonymous" to UNAUTHORIZED,
        "token without rights" to createAccessToken("unrouted", "secret", "elsewhere", gav, READ),
        "token with read on the private repository" to createAccessToken("internal-reader", "secret", "internal", gav, READ),
        "token with read on the hidden repository" to createAccessToken("shadow-reader", "secret", "shadow", gav, READ),
        "token with read on another repository" to createAccessToken("open-reader", "secret", "open", gav, READ),
        "manager token" to createManagerAccessToken("manager", "secret"),
        "session" to AccessTokenIdentifier(type = PRINCIPAL, value = 1),
    )

    private fun viewerOf(token: AccessTokenIdentifier?): Viewer =
        ViewerAccessFacadeImpl(mavenFacade).viewerOf(token?.let { AccessTokenDto(it, "token", LocalDate.of(2026, 1, 1), "") })

    @Test
    fun `should answer canReadRepository as the root listing decides`() {
        viewers().forEach { (viewer, token) ->
            val listed = findRepositories(token).toSet()
            val facadeViewer = viewerOf(token)
            repositoryNames.forEach { repository ->
                assertThat(facadeViewer.canReadRepository(repository))
                    .`as`("canReadRepository($repository) for $viewer")
                    .isEqualTo(repository in listed)
            }
        }
    }

    @Test
    fun `should answer canReadPath as the resource route decides`() {
        viewers().forEach { (viewer, token) ->
            val facadeViewer = viewerOf(token)
            repositoryNames.forEach { repository ->
                val route = mavenFacade.canAccessResource(token, mavenFacade.getRepository(repository)!!, Location.of(gav)).isOk
                assertThat(facadeViewer.canReadPath(repository, gav))
                    .`as`("canReadPath($repository, $gav) for $viewer")
                    .isEqualTo(route)
            }
        }
    }

    @Test
    fun `should answer canListRepository as the list route decides`() {
        viewers().forEach { (viewer, token) ->
            val listed = mavenFacade.listRepositories(token, RepositoryListQuery()).repositories.map { it.id }.toSet()
            val facadeViewer = viewerOf(token)
            repositoryNames.forEach { repository ->
                assertThat(facadeViewer.canListRepository(repository))
                    .`as`("canListRepository($repository) for $viewer")
                    .isEqualTo(repository in listed)
            }
        }
    }

    @Test
    fun `should list the repositories as the root listing does`() {
        viewers().forEach { (viewer, token) ->
            assertThat(viewerOf(token).listedRepositories())
                .`as`("listedRepositories() for $viewer")
                .isEqualTo(findRepositories(token).toSet())
        }
    }

    @Test
    fun `should answer false for a repository that does not exist`() {
        viewers().forEach { (viewer, token) ->
            val facadeViewer = viewerOf(token)
            assertThat(facadeViewer.canReadRepository("missing")).`as`("canReadRepository(missing) for $viewer").isFalse()
            assertThat(facadeViewer.canListRepository("missing")).`as`("canListRepository(missing) for $viewer").isFalse()
            assertThat(facadeViewer.canReadPath("missing", gav)).`as`("canReadPath(missing) for $viewer").isFalse()
        }
    }
}
