package com.reposilite

import org.testcontainers.mariadb.MariaDBContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Per-test resources on top of the shared containers from [SharedContainers].
 *
 * Every name carries a fresh random suffix, so two tests never address the same database, schema
 * or bucket, whatever order they run in and whichever fork they run in. MariaDB databases and
 * Postgres schemas are dropped again after the test; S3 buckets are left behind on the throwaway
 * Floci instance and are never read by another test.
 */
internal object IsolatedResources {

    private const val MARIADB_PORT = 3306

    fun createMariaDbDatabase(container: MariaDBContainer): String {
        val name = "test_${randomSuffix()}"

        adminMariaDb(container).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE DATABASE `$name`")
                statement.execute("GRANT ALL PRIVILEGES ON `$name`.* TO '${container.username}'@'%'")
            }
        }

        return name
    }

    fun dropMariaDbDatabase(container: MariaDBContainer, name: String) {
        adminMariaDb(container).use { connection ->
            connection.createStatement().use { it.execute("DROP DATABASE IF EXISTS `$name`") }
        }
    }

    fun createPostgresSchema(container: PostgreSQLContainer): String {
        val name = "test_${randomSuffix()}"

        adminPostgres(container).use { connection ->
            connection.createStatement().use { it.execute("CREATE SCHEMA \"$name\"") }
        }

        return name
    }

    fun dropPostgresSchema(container: PostgreSQLContainer, name: String) {
        adminPostgres(container).use { connection ->
            connection.createStatement().use { it.execute("DROP SCHEMA IF EXISTS \"$name\" CASCADE") }
        }
    }

    /** S3 bucket names are lower case DNS labels, so no underscores here. */
    fun newBucketName(prefix: String): String =
        "$prefix-${randomSuffix()}"

    private fun adminMariaDb(container: MariaDBContainer): Connection =
        DriverManager.getConnection(
            "jdbc:mariadb://${container.host}:${container.getMappedPort(MARIADB_PORT)}/",
            "root",
            container.password
        )

    private fun adminPostgres(container: PostgreSQLContainer): Connection =
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)

    private fun randomSuffix(): String =
        UUID.randomUUID().toString().replace("-", "")

}

/** Reposilite's database token for a Postgres schema of its own, see `DatabaseConnectionFactory`. */
internal fun PostgreSQLContainer.schemaToken(schema: String): String =
    "postgresql $host:${getMappedPort(5432)} $databaseName?currentSchema=$schema $username $password"

internal fun MariaDBContainer.databaseToken(database: String): String =
    "mariadb $host:${getMappedPort(3306)} $database $username $password"
