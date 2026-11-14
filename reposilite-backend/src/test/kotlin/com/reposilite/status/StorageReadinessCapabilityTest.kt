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

package com.reposilite.status

import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.shared.ErrorResponse
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import net.onelitefeather.ingot.storage.capability.ReadinessOutcome
import net.onelitefeather.ingot.storage.capability.ReadinessProbe
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import java.io.File
import kotlin.io.path.createDirectories

/**
 * A storage that declares its own readiness probe, which the status plugin asks instead of listing the root.
 */
private class ProbingStorage(delegate: StorageProvider, private val outcome: ReadinessOutcome) :
    StorageProvider by delegate, ReadinessProbe {

    override fun probe(): ReadinessOutcome = outcome

}

/** A storage whose root listing fails on the server side. */
private class FailingListingStorage(delegate: StorageProvider) : StorageProvider by delegate {

    override fun getFiles(location: Location): Result<List<Location>, ErrorResponse> =
        Result.error(ErrorResponse(500, "Failed to list /"))

}

internal class StorageReadinessCapabilityTest {

    @TempDir
    lateinit var workingDirectory: File

    private val logger = InMemoryLogger()

    private fun emptyRoot(name: String): StorageProvider =
        FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve(name).createDirectories(), "100%")

    @Test
    fun `a storage that declares a readiness probe answers with its own probe`() {
        // given: a storage whose root lists fine, but whose probe reports a failure with a reason
        val storage = ProbingStorage(emptyRoot("probing"), ReadinessOutcome.Failed("bucket is missing"))

        // when: the readiness of the storage is read
        val outcome = readinessOf(storage)

        // then: the outcome is the probe's, not the listing's
        assertThat(outcome).`as`("readiness of a storage with its own probe").isEqualTo(ReadinessOutcome.Failed("bucket is missing"))
    }

    @Test
    fun `a storage without a readiness probe is probed with the listing of its root`() {
        // when: the readiness of an empty root is read
        val outcome = readinessOf(emptyRoot("listing"))

        // then: an empty root is ready
        assertThat(outcome).`as`("readiness of an empty root").isEqualTo(ReadinessOutcome.Ok)
    }

    @Test
    fun `a missing root without a probe answers not found, which counts as answered`() {
        // given: a root that does not exist
        val storage = FileSystemStorageProviderFactory.of(logger, workingDirectory.toPath().resolve("missing"), "100%")

        // when: the readiness is read
        val outcome = readinessOf(storage)

        // then: the backend answered that the root does not exist
        assertThat(outcome).`as`("readiness of a missing root").isEqualTo(ReadinessOutcome.NotFoundOk)
    }

    @Test
    fun `a failing listing without a probe answers failed with the listing message`() {
        // when: the readiness of a root whose listing fails is read
        val outcome = readinessOf(FailingListingStorage(emptyRoot("failing")))

        // then: the failure is reported with the message of the listing
        assertThat(outcome).`as`("readiness of a failing listing").isEqualTo(ReadinessOutcome.Failed("Failed to list /"))
    }

}
