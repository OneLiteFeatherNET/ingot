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

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.SocketTimeoutException

/** The capabilities of the provider (design D9): usage, quota, and the readiness probe of the bucket. */
internal class GcsStorageProviderCapabilityTest {

    private val client = FakeGcsObjectClient()

    private val provider = GcsStorageProvider(
        failureFacade = FailureFacade(InMemoryLogger()),
        client = client,
        objectPrefix = "",
        chunkSize = 4,
        bucket = "repositories",
    )

    @Test
    fun `should report usage as minus one because the service reports no figure`() {
        // then: the usage is unknown, and the storage reports no usage of its own
        assertThat(provider.usage().get()).`as`("usage").isEqualTo(-1L)
        assertThat(provider.reportsUsage).`as`("reports usage").isFalse()
    }

    @Test
    fun `should report the capacity as the largest value because the bucket has no quota here`() {
        // then: the room a bucket can hold is not limited by the plugin
        assertThat(provider.canHold(1024L).get()).`as`("capacity").isEqualTo(Long.MAX_VALUE)
        assertThat(provider.supportsQuota).`as`("supports quota").isFalse()
    }

    @Test
    fun `should report ready when the bucket answers, empty or not`() {
        // when: the bucket answers its metadata request
        val outcome = provider.probe()

        // then: the probe is ok
        assertThat(outcome).`as`("outcome").isEqualTo(ReadinessOutcome.Ok)
    }

    @Test
    fun `should report a missing permission on the bucket and not an empty bucket`() {
        // given: the identity may not read the bucket
        client.bucketFailure = GcsServiceException(403, "forbidden")

        // when: the probe runs
        val outcome = provider.probe()

        // then: the probe fails with the permission as the reason
        assertThat(outcome).`as`("outcome").isInstanceOf(ReadinessOutcome.Failed::class.java)
        assertThat((outcome as ReadinessOutcome.Failed).reason).`as`("reason").contains("permission")
    }

    @Test
    fun `should report a bucket that does not exist as missing`() {
        // given: the bucket is not there
        client.bucketFailure = GcsServiceException(404, "not found")

        // when: the probe runs
        val outcome = provider.probe()

        // then: the probe fails with the missing bucket as the reason
        assertThat((outcome as ReadinessOutcome.Failed).reason).`as`("reason").contains("missing")
    }

    @Test
    fun `should report a bucket that does not answer in time as unreachable`() {
        // given: the request to the bucket times out
        client.bucketFailure = SocketTimeoutException("read timed out")

        // when: the probe runs
        val outcome = provider.probe()

        // then: the probe fails with unreachable as the reason
        assertThat((outcome as ReadinessOutcome.Failed).reason).`as`("reason").contains("unreachable")
    }

}
