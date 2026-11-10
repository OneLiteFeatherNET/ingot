/*
 * Copyright (c) 2023 dzikoysk
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

package com.reposilite.maven

import com.reposilite.maven.RepositoryVisibility.PRIVATE
import com.reposilite.maven.RepositoryVisibility.PUBLIC
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import net.onelitefeather.ingot.repository.RegisteredRepository
import net.onelitefeather.ingot.repository.RepositoryRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryRegistryTest : MavenSpecification() {

    override fun repositories() = listOf(
        RepositorySettings("releases", visibility = PUBLIC),
        RepositorySettings("private", visibility = PRIVATE),
        RepositorySettings("storing", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = true)
        )),
        RepositorySettings("caching", visibility = PUBLIC, proxied = mutableListOf(
            MirroredRepositorySettings(reference = REMOTE_REPOSITORY, store = false)
        )),
    )

    private fun registry(): RepositoryRegistry =
        mavenFacade.repositoryRegistry()

    @Test
    fun `registry lists repositories in configuration order`() {
        val names = registry().repositories().map { it.name }

        assertThat(names)
            .describedAs("the registry must list the repositories in the order of the configuration")
            .containsExactly("releases", "private", "storing", "caching")
    }

    @Test
    fun `registry returns the same repository instances as the maven facade`() {
        val registered: List<RegisteredRepository> = registry().repositories()

        assertThat(registered)
            .describedAs("the registry must hand out the live repository instances the maven facade serves")
            .containsExactlyElementsOf(mavenFacade.getRepositories())
        assertThat(registry().repository("private"))
            .describedAs("a lookup by name must return the instance the maven facade returns for that name")
            .isSameAs(mavenFacade.getRepository("private"))
    }

    @Test
    fun `registry returns null for an unknown repository`() {
        assertThat(registry().repository("unknown"))
            .describedAs("a repository that is not configured must not be found")
            .isNull()
    }

    @Test
    fun `registry finds a repository added through the settings without restart`() {
        mavenSettings.update(MavenSettings(repositories = repositories() + RepositorySettings(id = "extra")))

        assertThat(registry().repository("extra"))
            .describedAs("a repository added to the maven settings must be visible to the next lookup")
            .isNotNull()
    }

    @Test
    fun `registry no longer finds a repository removed from the settings`() {
        mavenSettings.update(MavenSettings(repositories = repositories().filter { it.id != "private" }))

        assertThat(registry().repository("private"))
            .describedAs("a repository removed from the maven settings must be gone from the registry")
            .isNull()
    }

    @Test
    fun `stores proxied files only for a repository with a storing mirror`() {
        assertThat(registry().repository("storing")?.storesProxiedFiles)
            .describedAs("a mirror with store = true must mark the repository as storing proxied files")
            .isTrue()
        assertThat(registry().repository("caching")?.storesProxiedFiles)
            .describedAs("a mirror with store = false must not mark the repository as storing proxied files")
            .isFalse()
        assertThat(registry().repository("releases")?.storesProxiedFiles)
            .describedAs("a repository without mirrors must not store proxied files")
            .isFalse()
    }

}
