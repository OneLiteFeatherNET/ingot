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

package net.onelitefeather.ingot.storage.azure

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The readiness probe and the usage of the provider (design D8 and D15). */
internal class AzureBlobStorageProviderReadinessTest {

    private val client = FakeAzureBlobClient()
    private val provider = AzureBlobStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        blobPrefix = "",
        blockSize = 8,
    )

    @Test
    fun `should report an empty container as healthy`() {
        // given: an existing container without any blob, which is the normal state of a new repository
        client.containerExists = true

        // when: the probe runs
        val outcome = provider.probe()

        // then: the container is healthy
        assertThat(outcome).`as`("probe outcome").isEqualTo(ReadinessOutcome.Ok)
    }

    @Test
    fun `should report a refused identity with the reason authorization and the role to grant`() {
        // given: the identity may not read the container
        client.containerRefused = true

        // when: the probe runs
        val outcome = provider.probe()

        // then: the reason names the authorization failure and the role of the blob data
        assertThat(outcome).isInstanceOf(ReadinessOutcome.Failed::class.java)
        val reason = (outcome as ReadinessOutcome.Failed).reason
        assertThat(reason).`as`("reason").startsWith("authorization").contains("Storage Blob Data Contributor")
    }

    @Test
    fun `should report a missing container as container missing`() {
        // given: the container does not exist
        client.containerExists = false

        // when: the probe runs
        val outcome = provider.probe()

        // then: the reason says the container is missing, which differs from an authorization failure
        assertThat(outcome).isEqualTo(ReadinessOutcome.Failed("container missing"))
    }

    @Test
    fun `should report usage as unknown and the room as unlimited`() {
        // then: the provider reports no usage and no quota, so the dashboard shows the repository as unlimited
        assertThat(provider.reportsUsage).`as`("reports usage").isFalse()
        assertThat(provider.supportsQuota).`as`("supports quota").isFalse()
        assertThat(provider.totalCapacityBytes()).`as`("capacity").isNull()
        assertThat(provider.usage().get()).`as`("usage in bytes").isEqualTo(-1L)
        assertThat(provider.canHold(1L).get()).`as`("room in bytes").isEqualTo(Long.MAX_VALUE)
    }

}
