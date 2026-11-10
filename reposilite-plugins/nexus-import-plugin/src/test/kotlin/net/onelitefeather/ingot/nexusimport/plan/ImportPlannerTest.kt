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

package net.onelitefeather.ingot.nexusimport.plan

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class ImportPlannerTest {

    private fun hosted(name: String, writePolicy: String = "ALLOW", versionPolicy: String = "RELEASE", components: Long = 2, bytes: Long = 100) =
        RepositoryFacts(name, "maven2", "hosted", writePolicy, versionPolicy, null, emptyList(), true, components, bytes)

    private fun proxy(name: String) =
        RepositoryFacts(name, "maven2", "proxy", null, "RELEASE", "https://repo.example.test/maven2/", emptyList(), true, 0, 0)

    private fun group(name: String, members: List<String>) =
        RepositoryFacts(name, "maven2", "group", null, null, null, members, true, 0, 0)

    private fun plan(vararg facts: RepositoryFacts, selection: Set<String>? = null, existing: Set<String> = emptySet()) =
        ImportPlanner(existingTargets = existing).plan(facts.toList(), selection)

    @Test
    fun `should map an allowing maven hosted repository to a redeployable private definition`() {
        val plan = plan(hosted("releases"))

        assertThat(plan.repositories.single().redeployment).isTrue
        assertThat(plan.repositories.single().type).isEqualTo(IngotType.HOSTED)
        assertThat(plan.rows.single().status).isEqualTo(LossStatus.MAPPED)
    }

    @Test
    fun `should map a deny write policy to a non redeployable repository with a partial row`() {
        val plan = plan(hosted("frozen", writePolicy = "DENY"))

        assertThat(plan.repositories.single().redeployment).isFalse
        assertThat(plan.rows.single().status).isEqualTo(LossStatus.PARTIAL)
    }

    @Test
    fun `should keep snapshots and report partial for a snapshot version policy`() {
        val plan = plan(hosted("snapshots", versionPolicy = "SNAPSHOT"))

        assertThat(plan.repositories.single().preserveSnapshots).describedAs("older builds must not be deleted").isTrue
        assertThat(plan.rows.single().status).isEqualTo(LossStatus.PARTIAL)
    }

    @Test
    fun `should map a proxy to a partial definition without credentials and name the remote`() {
        val plan = plan(proxy("central-proxy"))

        val definition = plan.repositories.single()
        assertThat(definition.type).isEqualTo(IngotType.PROXY)
        assertThat(definition.proxyRemoteUrl).isEqualTo("https://repo.example.test/maven2/")
        assertThat(plan.rows.single().status).isEqualTo(LossStatus.PARTIAL)
        assertThat(plan.rows.single().reason).contains("credentials")
    }

    @Test
    fun `should drop a foreign member of a group and report the dropped name`() {
        val npm = RepositoryFacts("npm-hosted", "npm", "hosted", "ALLOW", null, null, emptyList(), true, 1, 10)
        val plan = plan(hosted("releases"), npm, group("public", listOf("releases", "npm-hosted")))

        val groupRow = plan.rows.single { it.objectType == "repository" && it.nexusId == "public" }
        assertThat(groupRow.status).isEqualTo(LossStatus.PARTIAL)
        assertThat(groupRow.reason).contains("npm-hosted")
        assertThat(plan.repositories.single { it.type == IngotType.GROUP }.members).containsExactly("releases")
    }

    @Test
    fun `should mark a non maven format as unsupported with its component count and size`() {
        val docker = RepositoryFacts("docker-hosted", "docker", "hosted", "ALLOW", null, null, emptyList(), true, 7, 4096)
        val plan = plan(docker)

        assertThat(plan.rows.single().status).isEqualTo(LossStatus.UNSUPPORTED)
        assertThat(plan.rows.single().reason).contains("7 components").contains("4096 bytes")
        assertThat(plan.repositories).isEmpty()
    }

    @Test
    fun `should exclude a deselected repository and do nothing for it`() {
        val plan = plan(hosted("releases"), hosted("scratch"), selection = setOf("releases"))

        assertThat(plan.rows.single { it.nexusId == "scratch" }.status).isEqualTo(LossStatus.EXCLUDED)
        assertThat(plan.repositories.map { it.nexusName }).containsExactly("releases")
    }

    @Test
    fun `should propose a suffixed name when the name is taken and change nothing`() {
        val plan = plan(hosted("releases"), existing = setOf("releases"))

        assertThat(plan.repositories.single().targetName).isEqualTo("releases-nexus")
        assertThat(plan.blockers).isEmpty()
    }

    @Test
    fun `should block the job when even the suffixed name is taken`() {
        val plan = plan(hosted("releases"), existing = setOf("releases", "releases-nexus"))

        assertThat(plan.blockers).anySatisfy { assertThat(it).contains("releases") }
    }

    @Test
    fun `should report unreadable settings as manual rows with one area row and keep the content copyable`() {
        val forbidden = RepositoryFacts("releases", "maven2", "hosted", null, null, null, emptyList(), false, 2, 100)
        val plan = plan(forbidden)

        assertThat(plan.rows.map { it.status }).containsOnly(LossStatus.MANUAL)
        assertThat(plan.rows.count { it.objectType == "area" }).isEqualTo(1)
        assertThat(plan.repositories.single().definitionManual).isTrue
    }

    @Test
    fun `should estimate the transfer from the components of planned hosted repositories only`() {
        val plan = plan(hosted("a", bytes = 100), hosted("b", bytes = 250), proxy("p"))

        assertThat(plan.transferBytes).isEqualTo(350)
    }

    @Test
    fun `should count the formats of every discovered repository`() {
        val plan = plan(hosted("a"), proxy("p"), RepositoryFacts("n", "npm", "hosted", null, null, null, emptyList(), true, 0, 0))

        assertThat(plan.formats).isEqualTo(mapOf("maven2" to 2, "npm" to 1))
    }

    @Test
    fun `should render the loss report as csv with quoted fields`() {
        val row = LossRow("repository", "a,b", null, LossStatus.PARTIAL, "says \"hi\"", "consequence", "action")

        val csv = LossReport.csv(listOf(row))

        assertThat(csv.lines().first()).isEqualTo("objectType,nexusId,target,status,reason,consequence,action")
        assertThat(csv.lines()[1]).isEqualTo("\"repository\",\"a,b\",\"\",\"PARTIAL\",\"says \"\"hi\"\"\",\"consequence\",\"action\"")
    }
}
