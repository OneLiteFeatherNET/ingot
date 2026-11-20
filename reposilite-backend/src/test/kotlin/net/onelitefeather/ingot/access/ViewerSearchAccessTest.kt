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

@file:OptIn(net.onelitefeather.ingot.plugin.ExperimentalIngotPluginApi::class)

package net.onelitefeather.ingot.access

import com.reposilite.maven.RepositoryVisibility.HIDDEN
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.AccessTokenType.PRINCIPAL
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.api.AccessTokenDto
import net.onelitefeather.ingot.search.access.SearchAccessCompiler
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * [Viewer.searchAccess] is the search access value the core computes for the same subject and visibilities, see
 * expose-plugin-access-checks, group 5.
 */
internal class ViewerSearchAccessTest : MavenSpecification() {

    private val gav = "com/example/app/1.0/app-1.0.jar"

    override fun repositories() = listOf(
        RepositorySettings("open", visibility = PUBLIC),
        RepositorySettings("internal", visibility = PRIVATE),
        RepositorySettings("shadow", visibility = HIDDEN),
    )

    /** Every viewer of the matrix, by a name that appears in the failure message. */
    private fun viewers(): Map<String, AccessTokenIdentifier?> = mapOf(
        "anonymous" to UNAUTHORIZED,
        "token without rights" to createAccessToken("unrouted", "secret", "elsewhere", gav, READ),
        "token with read on the private repository" to createAccessToken("internal-reader", "secret", "internal", gav, READ),
        "token with read on the hidden repository" to createAccessToken("shadow-reader", "secret", "shadow", gav, READ),
        "manager token" to createManagerAccessToken("manager", "secret"),
        "session" to AccessTokenIdentifier(type = PRINCIPAL, value = 1),
    )

    private fun viewerOf(token: AccessTokenIdentifier?): Viewer =
        ViewerAccessFacadeImpl(mavenFacade, accessTokenFacade)
            .viewerOf(token?.let { AccessTokenDto(it, "token", LocalDate.of(2026, 1, 1), "") })

    @Test
    fun `should give each viewer the search access the compiler gives for its subject`() {
        val visibilities = mavenFacade.getRepositories().associate { it.name to it.visibility }

        viewers().forEach { (viewer, token) ->
            val subject = token?.let { accessTokenFacade.subjectOf(it) }
            assertThat(viewerOf(token).searchAccess())
                .`as`("searchAccess() for $viewer")
                .isEqualTo(SearchAccessCompiler.compile(subject, visibilities))
        }
    }

    @Test
    fun `should give a reader of a private repository the rows of that repository only`() {
        val access = viewerOf(createAccessToken("internal-reader", "secret", "internal", gav, READ)).searchAccess()

        assertThat(access.matches("internal", gav))
            .`as`("a row of the private repository the token may read")
            .isTrue()
        assertThat(access.matches("shadow", gav))
            .`as`("a row of a hidden repository the token holds no right on")
            .isFalse()
    }
}
