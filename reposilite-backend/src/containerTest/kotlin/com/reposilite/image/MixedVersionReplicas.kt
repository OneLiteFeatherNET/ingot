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

package com.reposilite.image

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/** The account the replicas run as, given to both releases through PUID and PGID. The shared directory belongs to it. */
internal const val SERVICE_ACCOUNT_ID: Int = 977

/** The bootstrap token both releases accept from `--token`, so every request of these tests authenticates the same way. */
internal val MIXED_BOOTSTRAP: Pair<String, String> = "bootstrap" to "bootstrap-secret-for-the-mixed-version-test"

/** The network, the database and the repository directory that the replicas of one case share. */
internal class SharedState(
    val network: Network,
    val database: MariaDb,
    val repositories: DataDirectory,
)

/**
 * Creates the state of one case: a fresh MariaDB database, and a repository directory inside [directory] that belongs to
 * the service account. Both are released when [block] returns, after the replicas that use them have been stopped.
 */
internal fun withSharedState(directory: Path, block: (SharedState) -> Unit) {
    val repositories = Files.createDirectories(directory.resolve("repositories"))
    Network.newNetwork().use { network ->
        MariaDb.start(network).use { database ->
            DataDirectory(repositories, ImageUnderTest.server).use { data ->
                data.chownTo(SERVICE_ACCOUNT_ID, SERVICE_ACCOUNT_ID)
                block(SharedState(network, database, data))
            }
        }
    }
}

/**
 * One replica of [image] on the shared state, not yet started. The repository directory is mounted where the server keeps
 * its repositories, so a deploy through one replica is on disk for the other one. PUID and PGID name the account that owns
 * that directory, as an operator sets them. [bus] and [instanceId] are set only for a case that runs the cluster bus.
 */
internal fun sharedRepositoryReplica(
    image: DockerImageName,
    state: SharedState,
    bus: String? = null,
    instanceId: String? = null,
): GenericContainer<*> =
    GenericContainer(image)
        .withExposedPorts(DEFAULT_SERVER_PORT)
        .withNetwork(state.network)
        .withEnv(
            buildMap {
                put("REPOSILITE_OPTS", "--token ${MIXED_BOOTSTRAP.first}:${MIXED_BOOTSTRAP.second}")
                put("INGOT_LOCAL_DATABASE", state.database.connection)
                put("REPOSILITE_LOCAL_DATABASE", state.database.connection)
                put("PUID", SERVICE_ACCOUNT_ID.toString())
                put("PGID", SERVICE_ACCOUNT_ID.toString())
                if (bus != null) {
                    put("INGOT_CLUSTER_URL", bus)
                    instanceId?.let { put("INGOT_CLUSTER_INSTANCE_ID", it) }
                }
            }
        )
        // The server keeps repositories in `repositories` under its working directory, which is /app/data.
        .withFileSystemBind(state.repositories.path.toString(), "/app/data/repositories", BindMode.READ_WRITE)
        .waitingFor(Wait.forHttp("/").forPort(DEFAULT_SERVER_PORT).forStatusCode(200))
        .withStartupTimeout(Duration.ofMinutes(3))

/**
 * A MariaDB server on [network] with one empty database for the case. Replicas reach it by IP, because the container name
 * does not resolve on the test network (see [UpgradeScenario]). The query helper runs as the service database user.
 */
internal class MariaDb private constructor(
    private val container: GenericContainer<*>,
    private val address: String,
) : AutoCloseable {

    /** The value of `INGOT_LOCAL_DATABASE` that points a replica at this database. */
    val connection: String
        get() = "mariadb $address:3306 $DB_NAME $DB_USER $DB_PASSWORD"

    /** Runs [sql] and returns its rows, each row split into its tab-separated columns. The output has no header. */
    fun query(sql: String): List<List<String>> {
        val result = container.execInContainer("mariadb", "-N", "-B", "-u$DB_USER", "-p$DB_PASSWORD", DB_NAME, "-e", sql)
        check(result.exitCode == 0) { "query failed with exit code ${result.exitCode}: ${result.stderr}" }
        return result.stdout.lines().filter { it.isNotEmpty() }.map { it.split("\t") }
    }

    override fun close() {
        container.stop()
    }

    companion object {
        fun start(network: Network): MariaDb {
            val container = GenericContainer(DockerImageName.parse(MARIADB_IMAGE))
                .withNetwork(network)
                .withEnv(
                    mapOf(
                        "MARIADB_ROOT_PASSWORD" to "root-password-for-the-mixed-version-test",
                        "MARIADB_DATABASE" to DB_NAME,
                        "MARIADB_USER" to DB_USER,
                        "MARIADB_PASSWORD" to DB_PASSWORD,
                    )
                )
                .waitingFor(Wait.forSuccessfulCommand("mariadb -h127.0.0.1 -u$DB_USER -p$DB_PASSWORD $DB_NAME -e 'SELECT 1'"))
                .withStartupTimeout(Duration.ofMinutes(3))
            container.start()
            val address = checkNotNull(container.containerInfo.networkSettings.networks.values.single().ipAddress) {
                "the MariaDB container has no address on the test network"
            }
            return MariaDb(container, address)
        }
    }
}

private const val MARIADB_IMAGE = "mariadb:11.4"
private const val DB_NAME = "ingot"
private const val DB_USER = "ingot"
private const val DB_PASSWORD = "database-password-for-the-mixed-version-test"
