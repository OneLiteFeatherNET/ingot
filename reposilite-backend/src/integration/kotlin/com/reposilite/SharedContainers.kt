package com.reposilite

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * One container per service for the whole test JVM.
 *
 * Containers are started on first use and never stopped by a test: starting one costs several
 * seconds, and the remote variants used to pay that cost for every single test method. Testcontainers'
 * Ryuk sidecar removes them when the JVM exits. The containers are shared, so the tests must not
 * share their state; [IsolatedResources] hands every test its own database, schema or bucket.
 */
internal object SharedContainers {

    val mariaDb: MariaDBContainer by lazy {
        MariaDBContainer(DockerImageName.parse(MARIADB_IMAGE).asCompatibleSubstituteFor("mariadb")).also { it.start() }
    }

    val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE).asCompatibleSubstituteFor("postgres")).also { it.start() }
    }

    val floci: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse(FLOCI_IMAGE))
            .withExposedPorts(FLOCI_PORT)
            .waitingFor(Wait.forListeningPort())
            .also { it.start() }
    }

    val nats: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse(NATS_IMAGE))
            .withExposedPorts(NATS_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .also { it.start() }
    }

    const val FLOCI_PORT = 4566

    /** Valkey as the cluster bus. Only publish and subscribe run against it, so no module or persistence is needed. */
    val valkey: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse(VALKEY_IMAGE))
            .withExposedPorts(VALKEY_PORT)
            .waitingFor(Wait.forListeningPort())
            .also { it.start() }
    }

    const val VALKEY_IMAGE = "valkey/valkey:8.1.11-alpine"
    const val VALKEY_PORT = 6379

    const val NATS_PORT = 4222

    const val NATS_IMAGE = "nats:2.12-alpine"

    /** Pinned by tag and digest, so that a run on another day uses the same bytes. Renovate raises both together. */
    const val MARIADB_IMAGE = "mariadb:13.0.2@sha256:54f493ea0d60b621a5bff84e5b20100f8b86b6efa6b79158e04756c5194a5e4d"
    const val POSTGRES_IMAGE = "postgres:18-alpine@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015"
    const val FLOCI_IMAGE = "floci/floci:2.4.0@sha256:5ee85db604c4aa74ac76e07ea5cc83086f37bb472bf3dbb97e34019e48efae9c"

}

/** Endpoint of the shared Floci instance as seen from the test JVM. */
internal fun GenericContainer<*>.s3Endpoint(): String =
    "http://$host:${getMappedPort(SharedContainers.FLOCI_PORT)}"

/** Client URL of the shared NATS broker as seen from the test JVM. */
internal fun GenericContainer<*>.natsUrl(): String =
    "nats://$host:${getMappedPort(SharedContainers.NATS_PORT)}"
