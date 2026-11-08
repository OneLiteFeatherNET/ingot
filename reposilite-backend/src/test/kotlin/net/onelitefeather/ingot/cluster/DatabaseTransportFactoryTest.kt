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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

/** The refusals of the database transport. They are decided before the database is touched, so no database is needed here. */
internal class DatabaseTransportFactoryTest {

    private val factory = DatabaseTransportFactory(
        database = { error("the database must not be opened for a refused bus URL") },
        clock = MutableClock(Instant.parse("2026-11-08T10:00:00Z")),
        scheduler = ManualPollScheduler(),
    )

    private val settings = ClusterSettings.read(
        ClusterSources(property = { null }, variable = mapOf("INGOT_CLUSTER_INSTANCE_ID" to "ingot-a")::get, hostName = { "host" })
    )

    @Test
    fun `a database url with a host is refused without echoing the host`() {
        assertThatThrownBy { factory.open(ClusterBusUrl.parse("database://broker.example:5432"), settings, "cluster-a") }
            .isInstanceOf(ClusterTransportRefusedException::class.java)
            .hasMessageContaining("database:")
            .hasMessageNotContaining("broker.example")
    }

    @Test
    fun `a database url with credentials is refused without echoing the credentials or the host`() {
        assertThatThrownBy { factory.open(ClusterBusUrl.parse("database://ops:s3cret@broker.example"), settings, "cluster-a") }
            .isInstanceOf(ClusterTransportRefusedException::class.java)
            .hasMessageNotContaining("s3cret")
            .hasMessageNotContaining("broker.example")
    }
}
