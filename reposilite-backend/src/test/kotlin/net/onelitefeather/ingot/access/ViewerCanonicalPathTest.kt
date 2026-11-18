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

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.api.Location
import com.reposilite.token.AccessTokenIdentifier
import com.reposilite.token.RoutePermission.READ
import com.reposilite.token.api.AccessTokenDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * [Viewer.canReadPath] judges a path only in its canonical form. A path that is not canonical is answered false, and an
 * unknown repository, an unreadable repository and a missing one give the same false.
 */
internal class ViewerCanonicalPathTest : MavenSpecification() {

    private val gav = "com/example/app/1.0/app-1.0.jar"

    override fun repositories() = listOf(
        RepositorySettings("open", visibility = PUBLIC),
        RepositorySettings("internal", visibility = PRIVATE),
    )

    private fun anonymous(): Viewer = viewerOf(UNAUTHORIZED)

    private fun viewerOf(token: AccessTokenIdentifier?): Viewer =
        ViewerAccessFacadeImpl(mavenFacade).viewerOf(token?.let { AccessTokenDto(it, "token", LocalDate.of(2026, 1, 1), "") })

    @Test
    fun `should answer the canonical path of a public repository as the route does`() {
        assertThat(anonymous().canReadPath("open", gav))
            .`as`("canReadPath(open, canonical gav) for an anonymous viewer")
            .isTrue()
    }

    @Test
    fun `should refuse a path with a dot-dot segment`() {
        assertThat(anonymous().canReadPath("open", "com/../com/example/app/1.0/app-1.0.jar"))
            .`as`("canReadPath with a .. segment")
            .isFalse()
    }

    @Test
    fun `should refuse a path with an encoded dot-dot segment`() {
        assertThat(anonymous().canReadPath("open", "%2e%2e/example/app/1.0/app-1.0.jar"))
            .`as`("canReadPath with an encoded .. segment")
            .isFalse()
    }

    @Test
    fun `should refuse a path with an empty segment`() {
        assertThat(anonymous().canReadPath("open", "com//example/app/1.0/app-1.0.jar"))
            .`as`("canReadPath with a doubled slash")
            .isFalse()
    }

    @Test
    fun `should refuse a path with a leading slash`() {
        assertThat(anonymous().canReadPath("open", "/$gav"))
            .`as`("canReadPath with a leading slash")
            .isFalse()
    }

    @Test
    fun `should refuse a path with an encoded slash`() {
        assertThat(anonymous().canReadPath("open", "com%2Fexample/app/1.0/app-1.0.jar"))
            .`as`("canReadPath with an encoded slash")
            .isFalse()
    }

    @Test
    fun `should refuse a repository name that contains a slash`() {
        assertThat(anonymous().canReadPath("open/com", "example/app/1.0/app-1.0.jar"))
            .`as`("canReadPath with a repository name containing a slash")
            .isFalse()
    }

    @Test
    fun `should answer the same false for an unreadable, a missing and a traversed repository`() {
        val viewer = anonymous()
        val unreadable = viewer.canReadPath("internal", gav)
        val missing = viewer.canReadPath("missing", gav)
        val traversed = viewer.canReadPath("open", "../internal/$gav")

        assertThat(unreadable).`as`("canReadPath(internal) for an anonymous viewer").isFalse()
        assertThat(missing).`as`("canReadPath(missing) for an anonymous viewer").isFalse()
        assertThat(traversed).`as`("canReadPath with a traversal into another repository").isFalse()
    }

    @Test
    fun `should answer a canonical path of a private repository with the read right as the route does`() {
        val token = createAccessToken("internal-reader", "secret", "internal", gav, READ)
        val route = mavenFacade.canAccessResource(token, mavenFacade.getRepository("internal")!!, Location.of(gav)).isOk

        assertThat(viewerOf(token).canReadPath("internal", gav))
            .`as`("canReadPath(internal, canonical gav) for a token with read")
            .isEqualTo(route)
    }

}
