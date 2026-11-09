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

import java.time.Duration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.Network
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 * Several replicas of the built image on one database, the way Kubernetes runs them (cluster bus, task 11.2).
 *
 * Two replicas of the new image share a MariaDB database and a NATS broker, and have to find each other through
 * `INGOT_CLUSTER_URL` alone. A third case runs the previous release beside the new image on the same database, the
 * rolling upgrade of a deployment: both have to keep answering, and the old one has to know nothing of the bus.
 *
 * Every wait is bounded. The instances list of the status fills when a replica publishes its first snapshot, which
 * happens one minute after its start, so that bound is longer than the others.
 */
class ClusterBusImageTest {

    @Test
    @DisplayName("two replicas on one database and one NATS broker see each other in the bus status")
    fun `two replicas see each other through the bus`() {
        Network.newNetwork().use { network ->
            withMariaDb(network) { database ->
                withNats(network) { bus ->
                    val first = replica(ImageUnderTest.server, network, database, "replica-a", bus)
                    first.use {
                        it.startOrExplain()
                        // One after the other: both would create the tables of a fresh database at the same moment.
                        replica(ImageUnderTest.server, network, database, "replica-b", bus).use { second ->
                            second.startOrExplain()

                            listOf(first, second).forEach { server ->
                                assertThat(Http.get(server.baseUrl()).isSuccessful).describedAs("a replica answers").isTrue()
                                val status = awaitStatus(server) { body ->
                                    STATE_CONNECTED in body && "\"id\":\"replica-a\"" in body && "\"id\":\"replica-b\"" in body
                                }
                                assertThat(status)
                                    .describedAs("the bus is connected and lists both replicas")
                                    .contains(STATE_CONNECTED, "\"transport\":\"nats\"", "\"id\":\"replica-a\"", "\"id\":\"replica-b\"")
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the previous release keeps serving beside the new image on one database")
    fun `previous release serves beside the new image`() {
        Network.newNetwork().use { network ->
            withMariaDb(network) { database ->
                // The previous release has no bus and no identity tables: it starts first, on the database it knows.
                replica(ImageUnderTest.previous, network, database, "previous", bus = null).use { previous ->
                    previous.startOrExplain()
                    assertThat(Http.get(previous.baseUrl(), BOOTSTRAP).isSuccessful).isTrue()

                    // The new image takes the same database over while the old one is still running.
                    replica(ImageUnderTest.server, network, database, "current", bus = null).use { current ->
                        current.startOrExplain()

                        assertThat(Http.get(current.baseUrl()).isSuccessful).describedAs("the new image answers").isTrue()
                        assertThat(Http.get("${current.baseUrl()}/api/status/instance", BOOTSTRAP).isSuccessful)
                            .describedAs("the new image answers an authenticated request")
                            .isTrue()
                        assertThat(Http.get(previous.baseUrl()).isSuccessful)
                            .describedAs("the previous release still answers once the new image runs")
                            .isTrue()
                        assertThat(Http.get("${previous.baseUrl()}/api/tokens", BOOTSTRAP).isSuccessful)
                            .describedAs("the previous release still answers an authenticated request on the shared database")
                            .isTrue()
                    }
                }
            }
        }
    }

    /** Polls the instance status of [server] until [accepted] holds for its body, within [STATUS_BOUND]. */
    private fun awaitStatus(server: GenericContainer<*>, accepted: (String) -> Boolean): String {
        val deadline = System.nanoTime() + STATUS_BOUND.toNanos()
        val pause = java.util.concurrent.CountDownLatch(1)
        var body = ""
        while (System.nanoTime() < deadline) {
            val response = Http.get("${server.baseUrl()}/api/status/instance", BOOTSTRAP)
            body = response.body
            if (response.isSuccessful && accepted(body)) {
                return body
            }
            pause.await(2, java.util.concurrent.TimeUnit.SECONDS)
        }
        return body
    }

    /**
     * One replica of [image] on [network]. The database comes from `INGOT_LOCAL_DATABASE`, the bus from
     * `INGOT_CLUSTER_URL`, and the temporary token is the one the status requests are made with.
     */
    private fun replica(
        image: DockerImageName,
        network: Network,
        database: String,
        instanceId: String,
        bus: String?,
    ): GenericContainer<*> =
        serverContainer(
            image = image,
            network = network,
            environment = buildMap {
                put("REPOSILITE_OPTS", "--token ${BOOTSTRAP.first}:${BOOTSTRAP.second}")
                put("INGOT_LOCAL_DATABASE", database)
                put("REPOSILITE_LOCAL_DATABASE", database)
                if (bus != null) {
                    put("INGOT_CLUSTER_URL", bus)
                    put("INGOT_CLUSTER_INSTANCE_ID", instanceId)
                }
            },
        )

    /** The address is an IP, because the container name did not resolve on the test network (see [UpgradeScenario]). */
    private fun withMariaDb(network: Network, block: (String) -> Unit) {
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
            .waitingFor(Wait.forSuccessfulCommand("mariadb -h127.0.0.1 -u$DB_USER -p$DB_PASSWORD $DB_NAME -e 'SELECT 1'"))
            .withStartupTimeout(Duration.ofMinutes(3))
            .use { database ->
                database.start()
                val address = database.containerInfo.networkSettings.networks.values.single().ipAddress
                block("mariadb $address:3306 $DB_NAME $DB_USER $DB_PASSWORD")
            }
    }

    private fun withNats(network: Network, block: (String) -> Unit) {
        GenericContainer(DockerImageName.parse(NATS_IMAGE))
            .withNetwork(network)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))
            .use { broker ->
                broker.start()
                val address = broker.containerInfo.networkSettings.networks.values.single().ipAddress
                block("nats://$address:4222")
            }
    }

    private companion object {
        val BOOTSTRAP = "bootstrap" to "bootstrap-secret-for-the-rehearsal"
        const val STATE_CONNECTED = "\"state\":\"connected\""
        val STATUS_BOUND: Duration = Duration.ofSeconds(180)

        const val NATS_IMAGE = "nats:2.12-alpine"
        const val MARIADB_IMAGE = "mariadb:11.4"
        const val DB_NAME = "ingot"
        const val DB_USER = "ingot"
        const val DB_PASSWORD = "database-password-for-the-rehearsal"
    }
}
