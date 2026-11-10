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

package com.reposilite.plugin.prometheus.metrics

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.StatusFacade
import com.reposilite.status.FailureFacade
import io.prometheus.metrics.model.registry.PrometheusRegistry
import io.prometheus.metrics.model.snapshots.GaugeSnapshot
import net.onelitefeather.ingot.readonly.ReadOnlyState
import net.onelitefeather.ingot.readonly.ReadOnlySource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.reactive.Reference

/** The read-only gauge of one instance. Each test registers into its own registry, so no metric is shared between tests. */
internal class ReadOnlyGaugeTest {

    private fun gaugeValue(enabled: Boolean): Double {
        val registry = PrometheusRegistry()
        val failureFacade = FailureFacade(InMemoryLogger())
        val status = StatusFacade(
            testEnv = true,
            maxThreads = Reference.reference(40),
            status = { true },
            remoteVersionUrl = "",
            failureFacade = failureFacade,
            readOnly = { ReadOnlyState(enabled = enabled, source = ReadOnlySource.STORED) },
        )
        ReposiliteMetrics.register(status, failureFacade, registry)

        val gauge = registry.scrape().single { it.metadata.prometheusName == "ingot_read_only" } as GaugeSnapshot
        return gauge.dataPoints.single().value
    }

    @Test
    fun `should report the read-only gauge as one while the instance is read-only`() {
        // when / then: the gauge reads one while read-only is on
        assertThat(gaugeValue(enabled = true)).`as`("ingot_read_only while read-only").isEqualTo(1.0)
    }

    @Test
    fun `should report the read-only gauge as zero while the instance is writable`() {
        // when / then: the gauge reads zero while read-only is off
        assertThat(gaugeValue(enabled = false)).`as`("ingot_read_only while writable").isEqualTo(0.0)
    }

}
