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

package com.reposilite.configuration.shared

import com.reposilite.configuration.ConfigurationFacade
import com.reposilite.configuration.InterleavingConfigurationRepository
import com.reposilite.configuration.SharedReposiliteConfigurationFacadeTest.TestSettings
import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import com.reposilite.configuration.shared.application.SharedConfigurationComponents
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.mutableReference
import java.nio.file.Path

/**
 * Two instances on one remote store race for the same domain. The interleavings are fixed by [InterleavingConfigurationRepository]:
 * the other instance writes between the moment one instance has checked its state and the moment it stores its document.
 */
internal class RemoteSharedConfigurationCompareAndSetTest {

    @TempDir
    lateinit var workingDirectory: Path

    private fun SharedConfigurationFacade.write(ifMatch: String?, property: String): Result<SharedSettingsSnapshot<TestSettings>, ErrorResponse> =
        updateDomain<TestSettings>("test", ifMatch) { TestSettings(property = property).asSuccess() }!!

    private fun storedProperty(instance: SharedConfigurationFacade): String =
        instance.getDomainSnapshot<TestSettings>("test")!!.settings.property

    private fun remoteInstance(configurationFacade: ConfigurationFacade): SharedConfigurationFacade =
        SharedConfigurationComponents(
            journalist = PrintStreamLogger(System.out, System.err),
            workingDirectory = workingDirectory,
            extensions = Extensions(PrintStreamLogger(System.out, System.err)),
            sharedConfigurationPath = null,
            failureFacade = FailureFacade(PrintStreamLogger(System.out, System.err)),
            configurationFacade = configurationFacade,
        ).sharedConfigurationFacade(
            sharedSettingsProvider = SharedSettingsProvider(mapOf(TestSettings::class.java to mutableReference(TestSettings()))),
        )

    @Test
    fun `should apply only one of two conditional writes that were both made against the same tag`() {
        // given: two instances that read the same state under the same tag
        val repository = InterleavingConfigurationRepository(InMemoryConfigurationRepository())
        val store = ConfigurationComponents().configurationFacade(configurationRepository = repository)
        val instanceA = remoteInstance(store)
        val instanceB = remoteInstance(store)
        val tag = instanceA.getDomainSnapshot<TestSettings>("test")!!.etag

        // and: instance B writes with that tag in the moment between A's check and A's store
        var writtenByB: Result<SharedSettingsSnapshot<TestSettings>, ErrorResponse>? = null
        repository.beforeNextCompareAndSave { writtenByB = instanceB.write(ifMatch = tag, property = "from-b") }

        // when: instance A writes with the same tag
        val writtenByA = instanceA.write(ifMatch = tag, property = "from-a")

        // then: B's write is the one applied, A's is refused against the state B stored
        assertThat(writtenByB!!.isOk).isTrue
        assertThat(writtenByA.isErr).isTrue
        assertThat(writtenByA.error.status).isEqualTo(412)
        assertThat(storedProperty(instanceB)).isEqualTo("from-b")
    }

    @Test
    fun `should repeat an unconditional write that lost the race and apply it on top of the other change`() {
        // given: two instances on one store
        val repository = InterleavingConfigurationRepository(InMemoryConfigurationRepository())
        val store = ConfigurationComponents().configurationFacade(configurationRepository = repository)
        val instanceA = remoteInstance(store)
        val instanceB = remoteInstance(store)

        // and: instance B stores a change in the moment between A's check and A's store
        repository.beforeNextCompareAndSave { instanceB.write(ifMatch = null, property = "from-b") }

        // when: instance A writes without a precondition
        val writtenByA = instanceA.write(ifMatch = null, property = "from-a")

        // then: the write is not refused, it is applied once A has seen B's state
        assertThat(writtenByA.isOk).isTrue
        assertThat(storedProperty(instanceB)).isEqualTo("from-a")
    }

    @Test
    fun `should replace the first document of a domain that another instance created meanwhile`() {
        // given: instance B stores the first document in the moment between A's check and A's store
        val repository = InterleavingConfigurationRepository(InMemoryConfigurationRepository())
        val store = ConfigurationComponents().configurationFacade(configurationRepository = repository)
        val instanceA = remoteInstance(store)
        val instanceB = remoteInstance(store)
        repository.beforeNextCompareAndSave { instanceB.write(ifMatch = null, property = "from-b") }

        // when: instance A writes an unconditional change
        val writtenByA = instanceA.write(ifMatch = null, property = "from-a")

        // then: A retries against the document B stored and replaces it, instead of failing or writing a stale copy
        assertThat(writtenByA.isOk).isTrue
        assertThat(storedProperty(instanceB)).isEqualTo("from-a")
    }

}
