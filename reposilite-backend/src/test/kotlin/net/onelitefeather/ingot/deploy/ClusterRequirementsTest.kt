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

package net.onelitefeather.ingot.deploy

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * The requirements are a pure function: every input is passed in as a value, so no test reads the environment, the
 * database, the network or the clock, and every test builds its own inputs.
 */
internal class ClusterRequirementsTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("unmetRequirements")
    fun `a worker refusal names each unmet requirement and no other`(
        name: String,
        database: String,
        clusterBusConfigured: Boolean,
        repositories: Map<String, String>,
        expectedMarkers: List<String>,
    ) {
        // when: the worker starts with the given shared state
        val refusal = refusalOf(
            target = DeployTarget.WORKER,
            database = database,
            clusterBusConfigured = clusterBusConfigured,
            repositories = repositories,
        )

        // then: the refusal starts with the subject and names only the unmet requirements
        assertThat(refusal).describedAs("refusal of $name").isNotNull()
        val line = refusal!!.line
        assertThat(line).describedAs("refusal line of $name")
            .startsWith("Ingot did not start: target worker needs shared state, but ")
        ALL_MARKERS.forEach { marker ->
            if (marker in expectedMarkers) {
                assertThat(line).describedAs("refusal line of $name names '$marker'").contains(marker)
            } else {
                assertThat(line).describedAs("refusal line of $name does not name '$marker'").doesNotContain(marker)
            }
        }
    }

    @Test
    fun `the refusal of a worker with every requirement unmet is one console line`() {
        // when: the worker starts with embedded SQLite, no bus and a file system repository
        val refusal = refusalOf(target = DeployTarget.WORKER, database = SQLITE, clusterBusConfigured = false, repositories = FILE_SYSTEM)

        // then: the line fits on one console line and ends with a full stop
        assertThat(refusal!!.line).describedAs("console line").doesNotContain("\n").endsWith(".")
    }

    @Test
    fun `the refusal of a worker ends the process with exit code 1`() {
        // when: the worker starts with embedded SQLite
        val refusal = refusalOf(target = DeployTarget.WORKER, database = SQLITE)

        // then: the exit code of a failed start
        assertThat(refusal!!.exitCode).describedAs("exit code").isEqualTo(1)
    }

    @Test
    fun `server with background jobs switched off names the switch and every unmet requirement`() {
        // when: server starts with the jobs switched off and no shared state
        val refusal = refusalOf(
            target = DeployTarget.SERVER,
            backgroundJobsEnabled = false,
            database = SQLITE,
            clusterBusConfigured = false,
            repositories = FILE_SYSTEM,
        )

        // then: the subject of the refusal is the switch, and all three requirements are named
        assertThat(refusal!!.line)
            .describedAs("refusal line")
            .startsWith("Ingot did not start: target server with INGOT_BACKGROUND_JOBS=false needs shared state, but ")
            .contains(EMBEDDED_DATABASE, MISSING_BUS, FILE_SYSTEM_REPOSITORY)
    }

    @Test
    fun `two file system repositories are both named in one refusal`() {
        // when: the worker starts with two repositories on the local file system
        val refusal = refusalOf(
            target = DeployTarget.WORKER,
            repositories = mapOf("releases" to "fs", "snapshots" to "fs"),
        )

        // then: both repository names are listed
        assertThat(refusal!!.line).describedAs("refusal line")
            .contains("repositories 'releases' and 'snapshots' are stored on the local file system")
    }

    @Test
    fun `a repository on object storage is not named in the refusal`() {
        // when: the worker starts with one file system repository and one on object storage
        val refusal = refusalOf(
            target = DeployTarget.WORKER,
            repositories = mapOf("releases" to "fs", "snapshots" to "s3"),
        )

        // then: only the file system repository is named
        assertThat(refusal!!.line).describedAs("refusal line")
            .contains("repository 'releases' is stored on the local file system")
            .doesNotContain("snapshots")
    }

    @Test
    fun `a worker with shared state starts`() {
        // when: the worker starts on PostgreSQL with a bus and object storage
        val refusal = refusalOf(target = DeployTarget.WORKER, database = POSTGRES, repositories = OBJECT_STORAGE)

        // then: no refusal
        assertThat(refusal).describedAs("refusal").isNull()
    }

    @Test
    fun `a file system repository starts on a worker when the shared filesystem is allowed`() {
        // when: the worker starts with the escape hatch set, on a file system repository
        val refusal = refusalOf(target = DeployTarget.WORKER, database = POSTGRES, repositories = FILE_SYSTEM, allowSharedFilesystem = true)

        // then: no refusal
        assertThat(refusal).describedAs("refusal").isNull()
    }

    @Test
    fun `server with background jobs switched off starts when the shared state is met`() {
        // when: server starts with the jobs switched off on PostgreSQL with a bus and object storage
        val refusal = refusalOf(
            target = DeployTarget.SERVER,
            backgroundJobsEnabled = false,
            database = POSTGRES,
            repositories = OBJECT_STORAGE,
        )

        // then: no refusal
        assertThat(refusal).describedAs("refusal").isNull()
    }

    @Test
    fun `target all never runs the check, even with every requirement unmet`() {
        // when: all starts with embedded SQLite, no bus and a file system repository
        val refusal = refusalOf(target = DeployTarget.ALL, database = SQLITE, clusterBusConfigured = false, repositories = FILE_SYSTEM)

        // then: the check does not apply, so nothing is refused
        assertThat(refusal).describedAs("refusal").isNull()
        assertThat(ClusterRequirements.applies(DeployTarget.ALL, backgroundJobsEnabled = true)).describedAs("applies").isFalse()
    }

    @Test
    fun `server with default settings never runs the check, even with every requirement unmet`() {
        // when: server starts with the default switch on embedded SQLite, no bus and a file system repository
        val refusal = refusalOf(target = DeployTarget.SERVER, database = SQLITE, clusterBusConfigured = false, repositories = FILE_SYSTEM)

        // then: the check does not apply, so existing installs start as before
        assertThat(refusal).describedAs("refusal").isNull()
        assertThat(ClusterRequirements.applies(DeployTarget.SERVER, backgroundJobsEnabled = true)).describedAs("applies").isFalse()
    }

    @Test
    fun `the check applies to a worker and to server with background jobs switched off`() {
        assertThat(ClusterRequirements.applies(DeployTarget.WORKER, backgroundJobsEnabled = true)).describedAs("worker").isTrue()
        assertThat(ClusterRequirements.applies(DeployTarget.SERVER, backgroundJobsEnabled = false)).describedAs("server off").isTrue()
    }

    @Test
    fun `the escape hatch variable is INGOT_ALLOW_SHARED_FILESYSTEM`() {
        assertThat(ClusterRequirements.ALLOW_SHARED_FILESYSTEM_VARIABLE).isEqualTo("INGOT_ALLOW_SHARED_FILESYSTEM")
    }

    @Test
    fun `the shared state refusal carries the startup refusal it was built from`() {
        // when: the wiring wraps a refusal into the exception the launcher catches
        val refusal = refusalOf(target = DeployTarget.WORKER, database = SQLITE)!!
        val exception = SharedStateRefusalException(refusal)

        // then: the launcher prints the same line and ends with the same exit code
        assertThat(exception.refusal).describedAs("refusal").isEqualTo(refusal)
    }

    companion object {

        private const val SQLITE = "sqlite reposilite.db"
        private const val POSTGRES = "postgresql://db.internal/ingot"
        private val FILE_SYSTEM = mapOf("releases" to "fs")
        private val OBJECT_STORAGE = mapOf("releases" to "s3")

        private const val EMBEDDED_DATABASE = "the database is embedded sqlite"
        private const val MISSING_BUS = "no cluster bus is configured"
        private const val FILE_SYSTEM_REPOSITORY = "is stored on the local file system"
        private val ALL_MARKERS = listOf(EMBEDDED_DATABASE, MISSING_BUS, FILE_SYSTEM_REPOSITORY)

        @JvmStatic
        fun unmetRequirements(): Stream<Arguments> = Stream.of(
            Arguments.of("embedded database alone", SQLITE, true, OBJECT_STORAGE, listOf(EMBEDDED_DATABASE)),
            Arguments.of("missing bus alone", POSTGRES, false, OBJECT_STORAGE, listOf(MISSING_BUS)),
            Arguments.of("file system repository alone", POSTGRES, true, FILE_SYSTEM, listOf(FILE_SYSTEM_REPOSITORY)),
            Arguments.of("all three together", SQLITE, false, FILE_SYSTEM, ALL_MARKERS),
        )

        /** The refusal of a start with the given sources. The escape hatch is off unless a test turns it on. */
        private fun refusalOf(
            target: DeployTarget = DeployTarget.WORKER,
            backgroundJobsEnabled: Boolean = true,
            database: String = POSTGRES,
            clusterBusConfigured: Boolean = true,
            repositories: Map<String, String> = OBJECT_STORAGE,
            allowSharedFilesystem: Boolean = false,
        ): StartupRefusal? = ClusterRequirements.refusalFor(
            target = target,
            backgroundJobsEnabled = backgroundJobsEnabled,
            database = database,
            clusterBusConfigured = clusterBusConfigured,
            repositoryStorageTypes = repositories,
            allowSharedFilesystem = allowSharedFilesystem,
        )
    }
}
