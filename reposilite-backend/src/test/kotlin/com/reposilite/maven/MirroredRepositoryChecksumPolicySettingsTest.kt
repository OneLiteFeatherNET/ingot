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

package com.reposilite.maven

import com.fasterxml.jackson.databind.JsonNode
import com.reposilite.ReposiliteObjectMapper
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.MirroredRepositorySettings
import com.reposilite.maven.application.RepositorySettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class MirroredRepositoryChecksumPolicySettingsTest {

    @Test
    fun `should refuse the warn checksum policy on a mirror without store`() {
        // given: a mirror that verifies its copies but does not keep them
        val settings = settingsWith(store = false, policy = "warn")

        // when: the settings are validated
        val violations = settings.violations()

        // then: the violation names the key and the repository
        assertThat(violations).describedAs("violations of warn without store").anyMatch { "checksumPolicy" in it && "releases" in it }
    }

    @Test
    fun `should refuse the fail checksum policy on a mirror without store`() {
        // given: a mirror that would have to verify copies it does not keep
        val settings = settingsWith(store = false, policy = "fail")

        // when: the settings are validated
        val violations = settings.violations()

        // then: the violation names the key and the repository
        assertThat(violations).describedAs("violations of fail without store").anyMatch { "checksumPolicy" in it && "releases" in it }
    }

    @Test
    fun `should accept the fail checksum policy on a mirror with store`() {
        // given: a mirror that keeps its copies
        val settings = settingsWith(store = true, policy = "fail")

        // when: the settings are validated
        val violations = settings.violations()

        // then: no violation concerns the checksum policy
        assertThat(violations).describedAs("violations of fail with store").noneMatch { "checksumPolicy" in it }
    }

    @Test
    fun `should load a mirror without a checksum policy key with the ignore policy`() {
        // given: a mirror written before the key existed
        val mirror = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(
            """{"reference":"https://releases.example/maven","store":true}""",
            MirroredRepositorySettings::class.java,
        )

        // when: the mirror is written back out
        val written = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(mirror)

        // then: the policy is ignore
        assertThat(written.get("checksumPolicy")?.asText()).describedAs("checksumPolicy of a mirror without the key").isEqualTo("ignore")
    }

    @Test
    fun `should read the checksum policy value fail from the configuration`() {
        // given: a mirror that names the policy in lower case, as the documentation states it
        val mirror = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(
            """{"reference":"https://releases.example/maven","store":true,"checksumPolicy":"fail"}""",
            MirroredRepositorySettings::class.java,
        )

        // when: the mirror is written back out
        val written = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.valueToTree<JsonNode>(mirror)

        // then: the value is kept as fail
        assertThat(written.get("checksumPolicy")?.asText()).describedAs("checksumPolicy after reading fail").isEqualTo("fail")
    }

    private fun settingsWith(store: Boolean, policy: String): MavenSettings {
        val mirror = ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER.readValue(
            """{"reference":"https://releases.example/maven","store":$store,"checksumPolicy":"$policy"}""",
            MirroredRepositorySettings::class.java,
        )
        return MavenSettings(repositories = listOf(RepositorySettings(id = "releases", proxied = listOf(mirror))))
    }

}
