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

package com.reposilite.maven.application

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.maven.EffectiveRepositoryType
import com.reposilite.maven.RepositoryType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val EXTERNAL = "https://example.com/releases"

internal class RepositoryTypeSettingsTest {

    private fun settings(vararg repositories: RepositorySettings) =
        MavenSettings(repositories = repositories.toList())

    private fun local(id: String, store: Boolean = false) =
        MirroredRepositorySettings(reference = id, store = store)

    private fun external(url: String = EXTERNAL) =
        MirroredRepositorySettings(reference = url)

    // --- hosted ---------------------------------------------------------------------------------------------------

    @Test
    fun `should accept a hosted repository without proxied repositories`() {
        val violations = settings(RepositorySettings(id = "releases", type = RepositoryType.HOSTED)).violations()

        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject a hosted repository that proxies and name the repository and the rule`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED, proxied = listOf(external()))
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'releases'")
            .contains("HOSTED")
            .contains("no proxied")
    }

    // --- proxy ----------------------------------------------------------------------------------------------------

    @Test
    fun `should accept a proxy that proxies external urls only`() {
        val violations = settings(
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external(), external("https://example.org/maven")))
        ).violations()

        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject a proxy without proxied repositories`() {
        val violations = settings(RepositorySettings(id = "central", type = RepositoryType.PROXY)).violations()

        assertThat(violations).singleElement().asString()
            .contains("'central'")
            .contains("PROXY")
    }

    @Test
    fun `should reject a proxy that proxies a local repository and name both repositories`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(local("releases"))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'central'")
            .contains("'releases'")
            .contains("GROUP")
    }

    @Test
    fun `should reject an empty proxied reference of a proxy`() {
        val violations = settings(
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external(), external(""))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'central'")
            .contains("empty")
    }

    // --- group ----------------------------------------------------------------------------------------------------

    @Test
    fun `should accept a group of existing local repositories`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "snapshots", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases"), local("snapshots"))),
        ).violations()

        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject a group without proxied repositories`() {
        val violations = settings(RepositorySettings(id = "all", type = RepositoryType.GROUP)).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("GROUP")
    }

    @Test
    fun `should reject an external url in a group and name it`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases"), external())),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains(EXTERNAL)
            .contains("GROUP")
    }

    @Test
    fun `should reject a group that references a repository which does not exist`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("ghost"))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("'ghost'")
    }

    @Test
    fun `should reject a group that proxies itself`() {
        val violations = settings(
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("all"))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("itself")
    }

    @Test
    fun `should reject a member of a group that stores, because a group stores nothing`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases", store = true))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("'releases'")
            .contains("store")
    }

    @Test
    fun `should reject an empty proxied reference of a group`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases"), local(""))),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("empty")
    }

    @Test
    fun `should reject a typed group that is part of a cycle and name the path`() {
        val violations = settings(
            RepositorySettings(id = "a", type = RepositoryType.GROUP, proxied = listOf(local("b"))),
            RepositorySettings(id = "b", type = RepositoryType.GROUP, proxied = listOf(local("a"))),
        ).violations()

        assertThat(violations).describedAs("violations of the cycle").anySatisfy { message ->
            assertThat(message).contains("'a'").contains("cycle").contains("a -> b -> a")
        }
    }

    @Test
    fun `should reject a typed group that reaches itself through an untyped repository`() {
        val violations = settings(
            RepositorySettings(id = "a", type = RepositoryType.GROUP, proxied = listOf(local("untyped"))),
            RepositorySettings(id = "untyped", proxied = listOf(local("a"))),
        ).violations()

        assertThat(violations).describedAs("violations of the cycle").anySatisfy { message ->
            assertThat(message).contains("'a'").contains("cycle")
        }
    }

    @Test
    fun `should not report a cycle that involves only untyped repositories`() {
        val violations = settings(
            RepositorySettings(id = "a", proxied = listOf(local("b"))),
            RepositorySettings(id = "b", proxied = listOf(local("a"))),
        ).violations()

        assertThat(violations).isEmpty()
    }

    // --- untyped (legacy) -----------------------------------------------------------------------------------------

    @Test
    fun `should keep an untyped group with an empty proxied entry valid and derive it as a group`() {
        // given: the group as it is in production, with an empty entry between its loopback references
        val group = RepositorySettings(
            id = "onelitefeather",
            proxied = listOf(local("onelitefeather-releases"), MirroredRepositorySettings(reference = ""), local("onelitefeather-snapshots")),
        )
        val settings = settings(
            RepositorySettings(id = "onelitefeather-releases"),
            RepositorySettings(id = "onelitefeather-snapshots"),
            group,
        )
        val localNames = setOf("onelitefeather-releases", "onelitefeather-snapshots", "onelitefeather")

        // then: the settings are valid, the group stays untyped and is derived as a group
        assertThat(settings.violations()).isEmpty()
        assertThat(group.type).isNull()
        assertThat(group.effectiveType(localNames)).isEqualTo(EffectiveRepositoryType.GROUP)
    }

    @Test
    fun `should derive proxy for an untyped repository whose proxied repositories are external`() {
        val repository = RepositorySettings(id = "central", proxied = listOf(external()))

        assertThat(repository.effectiveType(localNames = setOf("central"))).isEqualTo(EffectiveRepositoryType.PROXY)
    }

    @Test
    fun `should derive mixed for an untyped repository that proxies local and external repositories`() {
        val repository = RepositorySettings(id = "mixed", proxied = listOf(local("releases"), external()))

        assertThat(repository.effectiveType(localNames = setOf("releases", "mixed"))).isEqualTo(EffectiveRepositoryType.MIXED)
    }

    @Test
    fun `should derive hosted for an untyped repository without proxied repositories`() {
        assertThat(RepositorySettings(id = "releases").effectiveType(localNames = setOf("releases"))).isEqualTo(EffectiveRepositoryType.HOSTED)
    }

    @Test
    fun `should report the explicit type as derived whatever the proxied repositories are`() {
        val repository = RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(local("releases")))

        assertThat(repository.effectiveType(localNames = setOf("releases", "all"))).isEqualTo(EffectiveRepositoryType.GROUP)
    }

    // --- JSON -----------------------------------------------------------------------------------------------------

    @Test
    fun `should omit the type from the json when it is not set`() {
        val json = DEFAULT_OBJECT_MAPPER.readTree(DEFAULT_OBJECT_MAPPER.writeValueAsString(RepositorySettings(id = "releases")))

        // the storage provider carries a type of its own, so only the top level of the repository is checked
        assertThat(json.at("/type").isMissingNode).describedAs("top-level type of an untyped repository").isTrue
    }

    @Test
    fun `should write the type as its name when it is set`() {
        val json = DEFAULT_OBJECT_MAPPER.writeValueAsString(RepositorySettings(id = "all", type = RepositoryType.GROUP))

        assertThat(json).contains("\"type\":\"GROUP\"")
    }

    @Test
    fun `should read a repository written before the type existed as untyped`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue("""{ "repositories": [ { "id": "releases", "visibility": "PUBLIC" } ] }""", MavenSettings::class.java)

        assertThat(settings.repositories.single().type).isNull()
    }

}
