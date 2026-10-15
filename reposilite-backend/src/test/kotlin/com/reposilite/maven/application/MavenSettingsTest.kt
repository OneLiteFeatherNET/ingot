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
import com.reposilite.maven.RepositoryVisibility.PRIVATE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class MavenSettingsTest {

    @Test
    fun `should load a maven block written before tags and description existed with empty defaults`() {
        // given: a maven block as it was written before the description and tags were introduced
        val json = """
            {
              "repositories": [
                { "id": "releases", "visibility": "PUBLIC", "redeployment": false },
                { "id": "private", "visibility": "PRIVATE" }
              ]
            }
        """.trimIndent()

        // when: the block is read
        val settings = DEFAULT_OBJECT_MAPPER.readValue(json, MavenSettings::class.java)

        // then: the existing values are kept and both new fields take their defaults
        assertThat(settings.repositories.map { it.id }).containsExactly("releases", "private")
        assertThat(settings.repositories[1].visibility).isEqualTo(PRIVATE)
        settings.repositories.forEach { repository ->
            assertThat(repository.description).describedAs("description of '${repository.id}'").isEmpty()
            assertThat(repository.tags).describedAs("tags of '${repository.id}'").isEmpty()
        }
    }

    @Test
    fun `should accept a legacy maven block without violations`() {
        // given: a maven block without the new fields
        val settings = DEFAULT_OBJECT_MAPPER.readValue("""{ "repositories": [ { "id": "releases" } ] }""", MavenSettings::class.java)

        // when: the block is validated
        val violations = settings.violations()

        // then: the defaults are valid
        assertThat(violations).isEmpty()
    }

    @Test
    fun `should load an empty maven block with the default repositories`() {
        // when: an empty block is read
        val settings = DEFAULT_OBJECT_MAPPER.readValue("{}", MavenSettings::class.java)

        // then: the result is the default configuration
        assertThat(settings).isEqualTo(MavenSettings())
        assertThat(settings.violations()).isEmpty()
    }

    @Test
    fun `should round trip description and tags`() {
        // given: a repository with a description and tags
        val settings = MavenSettings(
            repositories = listOf(RepositorySettings(id = "releases", description = "Release builds", tags = listOf("team-a", "stable")))
        )

        // when: the settings are written and read back
        val json = DEFAULT_OBJECT_MAPPER.writeValueAsString(settings)
        val restored = DEFAULT_OBJECT_MAPPER.readValue(json, MavenSettings::class.java)

        // then: nothing is lost on the way
        assertThat(restored).isEqualTo(settings)
    }

    @Test
    fun `should accept a description of exactly 280 characters`() {
        // given: a description at the limit
        val settings = settingsWithDescription("a".repeat(280))

        // when: the settings are validated
        val violations = settings.violations()

        // then: the description is accepted
        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject a description longer than 280 characters and name the repository`() {
        // given: a description one character over the limit
        val settings = settingsWithDescription("a".repeat(281))

        // when: the settings are validated
        val violations = settings.violations()

        // then: exactly one violation names the repository and both lengths
        assertThat(violations).describedAs("violations").singleElement().asString()
            .contains("'releases'")
            .contains("280")
            .contains("281")
    }

    @ParameterizedTest
    @ValueSource(strings = ["team", "a", "0", "team-a", "a-b-c", "abcdefghijklmnopqrstuvwxyz012345"])
    fun `should accept a tag of lowercase letters, digits and hyphens with at most 32 characters`(tag: String) {
        // given: a repository tagged with a valid label
        val settings = settingsWithTags(tag)

        // when: the settings are validated
        val violations = settings.violations()

        // then: the tag is accepted
        assertThat(violations).describedAs("violations for tag '$tag'").isEmpty()
    }

    @ParameterizedTest
    @ValueSource(strings = ["Team", "-team", "team_a", "team a", "tëam", "team.a", "", "abcdefghijklmnopqrstuvwxyz0123456"])
    fun `should reject a tag that breaks the format and name the repository and the tag`(tag: String) {
        // given: a repository tagged with an invalid label
        val settings = settingsWithTags(tag)

        // when: the settings are validated
        val violations = settings.violations()

        // then: exactly one violation names the repository and the rejected tag
        assertThat(violations).describedAs("violations for tag '$tag'").singleElement().asString()
            .contains("'releases'")
            .contains("'$tag'")
    }

    @Test
    fun `should accept exactly ten tags`() {
        // given: a repository with the maximum number of tags
        val settings = settingsWithTags(*(1..10).map { "tag$it" }.toTypedArray())

        // when: the settings are validated
        val violations = settings.violations()

        // then: all of them are accepted
        assertThat(violations).isEmpty()
    }

    @Test
    fun `should reject eleven tags and name the repository and the count`() {
        // given: a repository with one tag too many
        val settings = settingsWithTags(*(1..11).map { "tag$it" }.toTypedArray())

        // when: the settings are validated
        val violations = settings.violations()

        // then: exactly one violation names the repository and the number of tags
        assertThat(violations).describedAs("violations").singleElement().asString()
            .contains("'releases'")
            .contains("10")
            .contains("11")
    }

    @Test
    fun `should reject a tag listed twice and name the repository and the tag`() {
        // given: a repository listing the same tag twice
        val settings = settingsWithTags("team", "stable", "team")

        // when: the settings are validated
        val violations = settings.violations()

        // then: exactly one violation names the repository and the duplicated tag
        assertThat(violations).describedAs("violations").singleElement().asString()
            .contains("'releases'")
            .contains("'team'")
            .contains("more than once")
    }

    @Test
    fun `should report the violations of every repository`() {
        // given: two repositories, each with a broken tag
        val settings = MavenSettings(
            repositories = listOf(
                RepositorySettings(id = "releases", tags = listOf("Team")),
                RepositorySettings(id = "snapshots", tags = listOf("team", "team")),
            )
        )

        // when: the settings are validated
        val violations = settings.violations()

        // then: each repository is named in its own violation
        assertThat(violations).hasSize(2)
        assertThat(violations.joinToString("\n"))
            .contains("'releases'")
            .contains("'snapshots'")
    }

    private fun settingsWithTags(vararg tags: String) =
        MavenSettings(repositories = listOf(RepositorySettings(id = "releases", tags = tags.toList())))

    private fun settingsWithDescription(description: String) =
        MavenSettings(repositories = listOf(RepositorySettings(id = "releases", description = description)))

}
