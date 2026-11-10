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

package net.onelitefeather.ingot.nexusimport.definitions

import com.reposilite.maven.RepositoryType
import com.reposilite.maven.RepositoryVisibility
import com.reposilite.maven.application.RepositorySettings
import net.onelitefeather.ingot.nexusimport.plan.IngotType
import net.onelitefeather.ingot.nexusimport.plan.PlannedRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class DefinitionWriterTest {

    private fun hosted(name: String, target: String = name, snapshots: Boolean = false) = PlannedRepository(
        nexusName = name, targetName = target, type = IngotType.HOSTED, redeployment = true,
        preserveSnapshots = snapshots, proxyRemoteUrl = null, members = emptyList(), definitionManual = false,
    )

    private fun existing(id: String) = RepositorySettings(id = id, visibility = RepositoryVisibility.PRIVATE, type = RepositoryType.HOSTED)

    @Test
    fun `should write every new definition in one update that carries the entity tag read at the start`() {
        // given: a domain with one existing repository and two planned ones
        val domain = FakeMavenDomain(listOf(existing("private")))

        // when: the definitions are written
        val result = DefinitionWriter(domain).write(listOf(hosted("releases"), hosted("snapshots")))

        // then: a single update carried the tag that was read, and both definitions were created
        assertThat(domain.updates.map { it.ifMatch }).describedAs("one update with the tag read first").containsExactly(domain.firstTag)
        assertThat(result.created).containsExactlyInAnyOrder("releases", "snapshots")
    }

    @Test
    fun `should retry once after a precondition failure and use the new tag`() {
        // given: another writer changes the domain between the read and the first update
        val domain = FakeMavenDomain(listOf(existing("private")))
        domain.changeBeforeNextUpdate(existing("other"))

        // when: the definitions are written
        val result = DefinitionWriter(domain).write(listOf(hosted("releases")))

        // then: the first update was refused, the second carried the new tag and succeeded
        assertThat(domain.updates).hasSize(2)
        // the other writer moved the domain from v1 to v2, so the retry must carry v2
        assertThat(domain.updates.map { it.ifMatch }).containsExactly(domain.firstTag, "v2")
        assertThat(result.created).containsExactly("releases")
    }

    @Test
    fun `should turn every definition into manual rows with its json when the domain is managed externally`() {
        // given: the shared settings file owns the maven domain
        val domain = FakeMavenDomain(listOf(existing("private")), managed = true)

        // when: the definitions are written
        val result = DefinitionWriter(domain).write(listOf(hosted("releases")))

        // then: nothing is stored, and the manual row carries the JSON to paste
        assertThat(domain.stored.map { it.id }).doesNotContain("releases")
        assertThat(result.created).isEmpty()
        assertThat(result.manual.single().json).contains("\"id\" : \"releases\"").contains("\"visibility\" : \"PRIVATE\"")
    }

    @Test
    fun `should leave every existing repository exactly as it was`() {
        // given: an existing repository with settings of its own
        val kept = existing("private").copy(description = "keep me")
        val domain = FakeMavenDomain(listOf(kept))

        // when: a definition is added
        DefinitionWriter(domain).write(listOf(hosted("releases")))

        // then: the existing repository is unchanged
        assertThat(domain.stored.single { it.id == "private" }).isEqualTo(kept)
    }

    @Test
    fun `should keep older snapshot builds for a snapshot repository`() {
        // given: a planned snapshot repository
        val domain = FakeMavenDomain(emptyList())

        // when: the definition is written
        DefinitionWriter(domain).write(listOf(hosted("snapshots", snapshots = true)))

        // then: preserveSnapshots is true
        assertThat(domain.stored.single { it.id == "snapshots" }.preserveSnapshots).isTrue
    }

    @Test
    fun `should not create a definition whose name the domain already holds`() {
        // given: the domain already holds the name the plan proposes
        val domain = FakeMavenDomain(listOf(existing("releases")))

        // when: the definition is written
        val result = DefinitionWriter(domain).write(listOf(hosted("releases")))

        // then: nothing is created and nothing is updated for it
        assertThat(result.created).isEmpty()
        assertThat(result.skipped).containsExactly("releases")
        assertThat(domain.updates).isEmpty()
    }
}
