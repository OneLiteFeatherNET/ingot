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

package net.onelitefeather.ingot.cluster

import com.reposilite.scheduling.ScheduledJobClaims
import com.reposilite.scheduling.infrastructure.SqlScheduledJobClaims
import org.jetbrains.exposed.v1.jdbc.Database
import java.time.Clock

/**
 * A bus that cannot start on this instance. The plugin logs the message once and keeps the instance serving without the bus.
 * The message never contains the bus URL.
 */
internal class ClusterTransportRefusedException(message: String) : RuntimeException(message)

/**
 * Opens the database transport on the instance's own database. It is refused on SQLite and H2, because a file database
 * cannot be shared by instances, and when the URL names a host or credentials.
 *
 * @param database the instance database, looked up when the transport opens
 */
internal class DatabaseTransportFactory(
    private val database: () -> Database,
    private val clock: Clock = Clock.systemUTC(),
    private val scheduler: PollScheduler = ExecutorPollScheduler,
    private val claims: (Database, Clock) -> ScheduledJobClaims = { db, time -> SqlScheduledJobClaims(db, time) },
    private val log: (String) -> Unit = {},
) : ClusterTransportFactory {

    override fun open(url: ClusterBusUrl, settings: ClusterSettings): ClusterTransport {
        if (url.host.isNotEmpty() || url.username != null || url.password != null) {
            throw ClusterTransportRefusedException(
                "The database cluster bus takes no host and no credentials, set INGOT_CLUSTER_URL to database: alone, the bus stays off"
            )
        }

        val current = database()
        if (current.vendor.lowercase() !in SHARED_VENDORS) {
            throw ClusterTransportRefusedException(
                "The database cluster bus needs MariaDB, MySQL or PostgreSQL, this instance runs on ${current.vendor}, the bus stays off"
            )
        }

        return DatabaseClusterTransport(
            store = SqlClusterEventStore(current),
            claims = claims(current, clock),
            clock = clock,
            scheduler = scheduler,
            pollIntervalMillis = POLL_INTERVAL_MILLIS,
            instanceId = settings.instanceId,
            log = log,
        )
    }

    private companion object {
        val SHARED_VENDORS = setOf("mariadb", "mysql", "postgresql")

        // Replaced by the configured poll interval once it is read from the settings.
        const val POLL_INTERVAL_MILLIS = 1000L
    }
}
