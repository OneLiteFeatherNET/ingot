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
        MariaDBContainer(DockerImageName.parse("mariadb:latest")).also { it.start() }
    }

    val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer(DockerImageName.parse("postgres:latest")).also { it.start() }
    }

    val floci: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("floci/floci:latest"))
            .withExposedPorts(FLOCI_PORT)
            .waitingFor(Wait.forListeningPort())
            .also { it.start() }
    }

    const val FLOCI_PORT = 4566

}

/** Endpoint of the shared Floci instance as seen from the test JVM. */
internal fun GenericContainer<*>.s3Endpoint(): String =
    "http://$host:${getMappedPort(SharedContainers.FLOCI_PORT)}"
