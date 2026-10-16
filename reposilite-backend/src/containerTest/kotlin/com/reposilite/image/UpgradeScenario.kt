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

package com.reposilite.image

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** Where the data of the rehearsal lives. SQLite is the default of both products. */
internal enum class DatabaseEngine { SQLITE, MARIADB }

/**
 * The upgrade an operator actually performs, rehearsed on one data directory in three phases:
 *
 * 1. the source image (an earlier Reposilite or Ingot) builds [RealisticDataset] and records the access matrix,
 * 2. Ingot takes the same directory over, and must answer every cell as the source did,
 * 3. the source gets the directory back, because a rollback is part of the risk assessment.
 *
 * The matrix recorded in phase 1 is the oracle. Nothing about what a route means is restated here, so the rehearsal
 * cannot disagree with the product about its own rights. The cells that are asserted by hand are the ones a regression
 * would look like: a prefix without a segment boundary, a write-only grant and a HIDDEN repository.
 */
internal class UpgradeScenario(
    private val source: DockerImageName,
    private val database: DatabaseEngine = DatabaseEngine.SQLITE,
) {

    fun run(directory: Path) {
        DataDirectory(directory, ImageUnderTest.server).use { data ->
            when (database) {
                DatabaseEngine.SQLITE -> phases(data, network = null)
                DatabaseEngine.MARIADB -> withMariaDb { network, address ->
                    // Written before the first start, as an operator who moved the database would have it.
                    Files.writeString(directory.resolve("configuration.cdn"), "database: mariadb $address $DB_NAME $DB_USER $DB_PASSWORD\n")
                    phases(data, network)
                }
            }
        }
    }

    private fun phases(data: DataDirectory, network: Network?) {
        val before = createOnSource(data, network)
        switchToIngot(data, before, network)
        handBackToSource(data, before, network)
    }

    /** What the source answered before the switch, which the later phases must reproduce. */
    private data class Baseline(
        val matrix: Map<String, Int>,
        val visibilities: List<String>,
        val frontendTitle: String?,
        val configuration: String = "",
    )

    private fun createOnSource(data: DataDirectory, network: Network?): Baseline {
        val baseline = serverContainer(
            image = source,
            data = data,
            environment = mapOf("REPOSILITE_OPTS" to "--token $BOOTSTRAP_NAME:$BOOTSTRAP_SECRET"),
            network = network,
        ).use { container ->
            container.startOrExplain()
            val url = container.baseUrl()
            val bootstrap = BOOTSTRAP_NAME to BOOTSTRAP_SECRET

            RealisticDataset.create(url, bootstrap)
            Baseline(
                matrix = RealisticDataset.accessMatrix(url, phase = "before-switch"),
                visibilities = RealisticDataset.visibilities(url, bootstrap),
                frontendTitle = RealisticDataset.frontendTitle(url, bootstrap),
            ).also { assertDocumentedRights(it.matrix) }
        }
        // Read once the source has stopped, so the file is the one it wrote last.
        return baseline.copy(configuration = data.read("configuration.cdn"))
    }

    /**
     * Some cells are asserted explicitly, so that the matrix cannot pass by comparing two equally wrong answers. Each one
     * is a rule an operator relies on.
     */
    private fun assertDocumentedRights(matrix: Map<String, Int>) {
        fun allowed(cell: String) = assertThat(matrix.getValue(cell)).describedAs("%s should be allowed", cell).isBetween(200, 299)
        fun denied(cell: String) = assertThat(matrix.getValue(cell) in 200..299).describedAs("%s should be denied (status %s)", cell, matrix[cell]).isFalse()

        allowed("reader GET ${RealisticDataset.ARTIFACT_PRIVATE}")
        denied("anonymous GET ${RealisticDataset.ARTIFACT_PRIVATE}")
        denied("reader-wrong-secret GET ${RealisticDataset.ARTIFACT_PRIVATE}")
        // "/rel" is a prefix without a segment boundary, so it covers "/releases" as well
        allowed("writer PUT releases")
        denied("writer PUT private")
        denied("reader PUT private")
        allowed("ci-deployer PUT snapshots")
        denied("ci-deployer PUT releases")
        allowed("pri-reader GET ${RealisticDataset.ARTIFACT_PRIVATE}")
        denied("pri-reader PUT private")
        allowed("ops-admin PUT hidden")
        denied("plain PUT releases")
    }

    private fun switchToIngot(data: DataDirectory, before: Baseline, network: Network?) {
        serverContainer(ImageUnderTest.server, data, network = network).use { ingot ->
            ingot.startOrExplain()
            val url = ingot.baseUrl()
            val manager = "ops-admin" to RealisticDataset.tokens.getValue("ops-admin")

            assertSameAnswers(before.matrix, RealisticDataset.accessMatrix(url, phase = "after-switch"), "after the switch")
            assertThat(RealisticDataset.visibilities(url, manager))
                .describedAs("repository visibility survives the switch")
                .isEqualTo(before.visibilities)
            assertThat(RealisticDataset.frontendTitle(url, manager))
                .describedAs("frontend settings survive the switch")
                .isEqualTo(before.frontendTitle)

            RealisticDataset.assertTokenApi(url, manager)
            RealisticDataset.assertMachinePrincipals(url, manager)
            RealisticDataset.assertIdentityDomains(url, manager)
            RealisticDataset.assertPublicArtifacts(url)
            assertConfigurationKept(before.configuration, data.read("configuration.cdn"))

            // Created through Ingot after the switch, so the source cannot know about it after the rollback.
            val created = Http.putJson(
                url = "$url/api/tokens/$AFTER_SWITCH_TOKEN",
                json = """{"type":"PERSISTENT","secretType":"RAW","secret":"$AFTER_SWITCH_SECRET","permissions":[],"routes":[{"path":"/releases","permissions":["r"]}]}""",
                credentials = manager,
            )
            assertThat(created.isSuccessful).describedAs("create a token in Ingot: %s", created.body).isTrue()

            assertThat(ingot.logs)
                .describedAs("the logging backend reported a missing writer")
                .doesNotContain("LOGGER ERROR")
        }
    }

    private fun handBackToSource(data: DataDirectory, before: Baseline, network: Network?) {
        // A plain image swap back, with no bootstrap token: the rollback is what an operator does, not a special setup.
        serverContainer(source, data, network = network)
            .use { rolledBack ->
                rolledBack.startOrExplain()
                val url = rolledBack.baseUrl()

                assertSameAnswers(before.matrix, RealisticDataset.accessMatrix(url, phase = "after-rollback"), "after the rollback")
                RealisticDataset.assertPublicArtifacts(url)

                // Spec 4.6: tokens created in Ingot after the migration are not in the source's tables, so they do not authenticate.
                val afterSwitch = Http.put(
                    url = "$url/releases/com/example/probe/after-switch/write.txt",
                    body = "not allowed",
                    credentials = AFTER_SWITCH_TOKEN to AFTER_SWITCH_SECRET,
                )
                assertThat(afterSwitch.isSuccessful)
                    .describedAs("a token created in Ingot after the switch must not work on the source (downgrade caveat)")
                    .isFalse()
            }
    }

    /**
     * Every cell answers as the recorded baseline, with one exception. A baseline answer of 5xx is a defect, not behaviour
     * an operator depends on, so the switch may answer it correctly. Ingot 1.1.0 answers a DELETE of a missing file with
     * 500 where upstream answers 200, and that is the only kind of change allowed. Any other difference is a regression.
     */
    private fun assertSameAnswers(baseline: Map<String, Int>, actual: Map<String, Int>, phase: String) {
        val regressions = baseline
            .filter { (cell, status) ->
                val now = actual[cell]
                now != status && !(status >= 500 && now != null && now < 500)
            }
            .mapValues { (cell, status) -> "$status -> ${actual[cell]}" }
        assertThat(regressions)
            .describedAs("cells that answer differently %s", phase)
            .isEmpty()
        assertThat(actual.keys).describedAs("the matrix covers the same cells %s", phase).isEqualTo(baseline.keys)
    }

    /**
     * Starts a MariaDB server on a network of its own and hands the block its address on that network. The database is
     * created empty for every test and is not reused, so nothing a test leaves behind can reach another one.
     *
     * The address is an IP rather than the container's name: the name did not resolve from the Reposilite container on
     * the test network, which is a property of the test harness and not of the product under test.
     */
    private fun withMariaDb(block: (Network, String) -> Unit) {
        Network.newNetwork().use { network ->
            GenericContainer(DockerImageName.parse(MARIADB_IMAGE))
                .withNetwork(network)
                .withEnv(
                    mapOf(
                        "MARIADB_ROOT_PASSWORD" to "root-password-for-the-rehearsal",
                        "MARIADB_DATABASE" to DB_NAME,
                        "MARIADB_USER" to DB_USER,
                        "MARIADB_PASSWORD" to DB_PASSWORD,
                    )
                )
                // The log says "ready" twice, once for the initialisation server on a socket only. A real query is the
                // only answer that means the server accepts the connection Reposilite is about to open.
                .waitingFor(Wait.forSuccessfulCommand("mariadb -h127.0.0.1 -u$DB_USER -p$DB_PASSWORD $DB_NAME -e 'SELECT 1'"))
                .withStartupTimeout(Duration.ofMinutes(3))
                .use { database ->
                    database.start()
                    val address = database.containerInfo.networkSettings.networks.values.single().ipAddress
                    block(network, "$address:$MARIADB_PORT")
                }
        }
    }

    private companion object {
        const val BOOTSTRAP_NAME = "bootstrap"
        const val BOOTSTRAP_SECRET = "bootstrap-secret-for-the-rehearsal"
        const val AFTER_SWITCH_TOKEN = "after-switch"
        const val AFTER_SWITCH_SECRET = "after-switch-secret-for-the-rehearsal"

        const val MARIADB_IMAGE = "mariadb:11.4"
        const val MARIADB_PORT = 3306
        const val DB_NAME = "reposilite"
        const val DB_USER = "reposilite"
        const val DB_PASSWORD = "database-password-for-the-rehearsal"
    }
}

/** Settings Ingot may add to configuration.cdn on its own, because they did not exist before the upgrade. */
private val SETTINGS_ADDED_BY_INGOT = setOf("hideSettingsSecrets")

/**
 * Both products rewrite configuration.cdn on startup, so the header comments differ and that is expected. What must not
 * change is the value of every setting that was already present, because a changed value would reconfigure an instance
 * that only meant to change its image. A setting that did not exist before may be added, but only one that Ingot knows
 * about and that keeps its default, which upstream ignores or preserves.
 */
internal fun assertConfigurationKept(before: String, after: String) {
    val previous = settingsOf(before)
    val current = settingsOf(after)
    assertThat(current.filterKeys { it in previous.keys })
        .describedAs("values of the settings that were already set")
        .isEqualTo(previous)
    assertThat(current.keys - previous.keys)
        .describedAs("settings the image added on its own")
        .isSubsetOf(SETTINGS_ADDED_BY_INGOT)
}

private fun settingsOf(configuration: String): Map<String, String> =
    configuration.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && ":" in it }
        .associate { line -> line.substringBefore(":").trim() to line.substringAfter(":").trim() }
