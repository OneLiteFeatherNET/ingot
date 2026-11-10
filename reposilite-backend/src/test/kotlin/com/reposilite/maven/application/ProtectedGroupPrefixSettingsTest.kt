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

package com.reposilite.maven.application

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.maven.RepositoryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val EXTERNAL = "https://example.com/releases"

internal class ProtectedGroupPrefixSettingsTest {

    private fun settings(vararg repositories: RepositorySettings) =
        MavenSettings(repositories = repositories.toList())

    private fun local(id: String) =
        MirroredRepositorySettings(reference = id)

    private fun external(url: String = EXTERNAL) =
        MirroredRepositorySettings(reference = url)

    @Test
    fun `should load a repository without the key with an empty list of protected prefixes`() {
        // given: a repository entry from an existing configuration file, which has no protectedGroupPrefixes key
        val json = """{"id":"all","type":"GROUP","proxied":[{"reference":"releases"}]}"""

        // when: the entry is read the way the configuration file is read
        val repository = DEFAULT_OBJECT_MAPPER.readValue(json, RepositorySettings::class.java)

        // then: the list is empty and the entry is accepted as before
        assertThat(repository.protectedGroupPrefixes).describedAs("protected prefixes of an existing entry").isEmpty()
    }

    @Test
    fun `should accept a group with protected prefixes`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "snapshots", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases"), local("snapshots")), protectedGroupPrefixes = listOf("net.onelitefeather")),
        ).violations()

        assertThat(violations).describedAs("violations of a group with a prefix").isEmpty()
    }

    @Test
    fun `should refuse protected prefixes on a proxy and name the key and the repository`() {
        val violations = settings(
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external()), protectedGroupPrefixes = listOf("net.onelitefeather")),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("protectedGroupPrefixes")
            .contains("'central'")
    }

    @Test
    fun `should refuse protected prefixes on a hosted repository and name the key and the repository`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED, protectedGroupPrefixes = listOf("net.onelitefeather")),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("protectedGroupPrefixes")
            .contains("'releases'")
    }

    @Test
    fun `should refuse protected prefixes on a mixed repository and name the key and the repository`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "mixed", proxied = listOf(local("releases"), external()), protectedGroupPrefixes = listOf("net.onelitefeather")),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("protectedGroupPrefixes")
            .contains("'mixed'")
    }

    @Test
    fun `should refuse protected prefixes on an untyped repository that is hosted`() {
        val violations = settings(
            RepositorySettings(id = "releases", protectedGroupPrefixes = listOf("net.onelitefeather")),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("protectedGroupPrefixes")
            .contains("'releases'")
    }

    @Test
    fun `should refuse a blank protected prefix and name it`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases")), protectedGroupPrefixes = listOf("  ")),
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("protectedGroupPrefixes")
            .contains("'all'")
    }

    @Test
    fun `should refuse a protected prefix that starts with a dot and name it`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases")), protectedGroupPrefixes = listOf(".net.onelitefeather")),
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
        ).violations()

        assertThat(violations).singleElement().asString().contains(".net.onelitefeather")
    }

    @Test
    fun `should refuse a protected prefix written with a slash and name it`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases")), protectedGroupPrefixes = listOf("net/onelitefeather")),
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
        ).violations()

        assertThat(violations).singleElement().asString().contains("net/onelitefeather")
    }

    @Test
    fun `should refuse a repeated protected prefix and name it`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases")), protectedGroupPrefixes = listOf("net.onelitefeather", "net.onelitefeather")),
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("net.onelitefeather")
            .contains("'all'")
    }

}
