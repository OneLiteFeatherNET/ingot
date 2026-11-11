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

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.maven.RepositoryType
import com.reposilite.maven.VersionPolicy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class RepositoryPolicySettingsTest {

    private fun settings(vararg repositories: RepositorySettings) =
        MavenSettings(repositories = repositories.toList())

    private fun external(url: String = "https://example.com/releases") =
        MirroredRepositorySettings(reference = url)

    private fun storedFieldsOf(repository: RepositorySettings): JsonNode =
        DEFAULT_OBJECT_MAPPER.valueToTree(repository)

    // --- stored document ------------------------------------------------------------------------------------------

    @Test
    fun `should write no version policy and no read-only key for a repository that sets neither`() {
        // given: a repository edited in the dashboard, where only its description changed
        val repository = RepositorySettings(id = "releases", description = "Libraries of the team")

        // when: the repository is stored
        val stored = storedFieldsOf(repository)

        // then: neither new key is written, so the document is the same as before the change
        assertThat(stored.has("versionPolicy")).describedAs("versionPolicy is written for an untouched repository").isFalse
        assertThat(stored.has("readOnly")).describedAs("readOnly is written for an untouched repository").isFalse
    }

    @Test
    fun `should keep an explicit version policy and read-only flag in the stored document`() {
        // given: a repository whose operator chose a release policy and froze it
        val repository = RepositorySettings(id = "releases", versionPolicy = VersionPolicy.RELEASE, readOnly = true)

        // when: the repository is stored
        val stored = storedFieldsOf(repository)

        // then: both keys are written with the values the operator chose
        assertThat(stored.get("versionPolicy")?.asText()).isEqualTo("RELEASE")
        assertThat(stored.get("readOnly")?.asBoolean()).isTrue
    }

    @Test
    fun `should read a repository document of an earlier version with both keys absent`() {
        // given: a repository document as the previous release wrote it
        val document = """{ "id": "releases", "visibility": "PUBLIC", "redeployment": true }"""

        // when: the document is read
        val repository = DEFAULT_OBJECT_MAPPER.readValue(document, RepositorySettings::class.java)

        // then: both keys stay absent, and the repository validates as before
        assertThat(repository.versionPolicy).describedAs("versionPolicy of a legacy document").isNull()
        assertThat(repository.readOnly).describedAs("readOnly of a legacy document").isNull()
        assertThat(settings(repository).violations()).isEmpty()
    }

    // --- validation on opt-in -------------------------------------------------------------------------------------

    @Test
    fun `should accept both keys on an untyped repository, which takes uploads`() {
        val violations = settings(RepositorySettings(id = "snapshots", versionPolicy = VersionPolicy.SNAPSHOT, readOnly = false)).violations()

        assertThat(violations).isEmpty()
    }

    @Test
    fun `should accept both keys on a hosted repository`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED, versionPolicy = VersionPolicy.RELEASE, readOnly = true),
        ).violations()

        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject a version policy on a group and name the repository and the key`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(MirroredRepositorySettings(reference = "releases")), versionPolicy = VersionPolicy.RELEASE),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("versionPolicy")
            .contains("GROUP")
    }

    @Test
    fun `should reject a version policy on a proxy and name the repository and the key`() {
        val violations = settings(
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external()), versionPolicy = VersionPolicy.MIXED),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'central'")
            .contains("versionPolicy")
            .contains("PROXY")
    }

    @Test
    fun `should reject a read-only flag on a proxy and name the repository and the key`() {
        val violations = settings(
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external()), readOnly = true),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'central'")
            .contains("readOnly")
            .contains("PROXY")
    }

    @Test
    fun `should reject a read-only flag on a group and name the repository and the key`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(MirroredRepositorySettings(reference = "releases")), readOnly = false),
        ).violations()

        assertThat(violations).singleElement().asString()
            .contains("'all'")
            .contains("readOnly")
            .contains("GROUP")
    }

    @Test
    fun `should keep validating a proxy and a group that set neither key by the old rules only`() {
        val violations = settings(
            RepositorySettings(id = "releases", type = RepositoryType.HOSTED),
            RepositorySettings(id = "central", type = RepositoryType.PROXY, proxied = listOf(external())),
            RepositorySettings(id = "all", type = RepositoryType.GROUP, proxied = listOf(MirroredRepositorySettings(reference = "releases"))),
        ).violations()

        assertThat(violations).isEmpty()
    }

}
