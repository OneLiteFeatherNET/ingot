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
import com.reposilite.configuration.SharedReposiliteConfigurationFacadeTest.TestSettings
import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.shared.application.SharedConfigurationComponents
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.configuration.specification.SharedReposiliteConfigurationSpecification
import com.reposilite.plugin.Extensions
import com.reposilite.shared.ErrorResponse
import com.reposilite.status.FailureFacade
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.Result
import panda.std.asSuccess
import panda.std.reactive.mutableReference

internal class SharedSettingsEntityTagTest : SharedReposiliteConfigurationSpecification() {

    private fun SharedConfigurationFacade.write(ifMatch: String?, property: String): Result<SharedSettingsSnapshot<TestSettings>, ErrorResponse> =
        updateDomain<TestSettings>("test", ifMatch) { TestSettings(property = property).asSuccess() }!!

    /**
     * Builds a facade on the remote provider, the way a multi-instance deployment does: every instance keeps its own
     * in-memory copy and only the shared store is common to all of them.
     */
    private fun remoteInstance(configurationFacade: ConfigurationFacade): SharedConfigurationFacade =
        SharedConfigurationComponents(
            journalist = PrintStreamLogger(System.out, System.err),
            workingDirectory = workingDirectory.toPath(),
            extensions = Extensions(PrintStreamLogger(System.out, System.err)),
            sharedConfigurationPath = null,
            failureFacade = FailureFacade(PrintStreamLogger(System.out, System.err)),
            configurationFacade = configurationFacade,
        ).sharedConfigurationFacade(
            sharedSettingsProvider = SharedSettingsProvider(mapOf(TestSettings::class.java to mutableReference(TestSettings()))),
        )

    @Test
    fun `should derive the same entity tag for unchanged settings`() {
        // when: the domain is read twice without any change
        val first = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!
        val second = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!

        // then: both reads carry the same entity tag
        assertThat(first.etag).isEqualTo(second.etag)
    }

    @Test
    fun `should change the entity tag after the domain has been updated`() {
        // given: a domain read before an update
        val before = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!

        // when: the domain is updated
        sharedConfigurationFacade.write(ifMatch = null, property = "changed")

        // then: the entity tag reflects the new state
        assertThat(sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!.etag).isNotEqualTo(before.etag)
    }

    @Test
    fun `should write without precondition when no if-match is sent`() {
        // when: a write without if-match is sent
        val result = sharedConfigurationFacade.write(ifMatch = null, property = "unconditional")

        // then: it is applied as it always was
        assertThat(result.isOk).isTrue
        assertThat(sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!.settings.property).isEqualTo("unconditional")
    }

    @Test
    fun `should write and return the new entity tag when if-match is current`() {
        // given: the entity tag of the current state
        val current = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!.etag

        // when: a write carries that entity tag
        val result = sharedConfigurationFacade.write(ifMatch = current, property = "conditional")

        // then: the write is applied and the returned entity tag belongs to the stored state
        assertThat(result.isOk).isTrue
        val stored = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!
        assertThat(result.get().etag).isEqualTo(stored.etag).isNotEqualTo(current)
    }

    @Test
    fun `should reject a write with a stale entity tag and keep the stored settings`() {
        // given: a tag read before someone else updated the domain
        val stale = sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!.etag
        sharedConfigurationFacade.write(ifMatch = null, property = "other-admin")

        // when: a write with the stale tag arrives
        val result = sharedConfigurationFacade.write(ifMatch = stale, property = "overwrite")

        // then: it is rejected with 412 and names the domain
        assertThat(result.isErr).isTrue
        assertThat(result.error.status).isEqualTo(412)
        assertThat(result.error.message).contains("'test'")

        // then: the other admin's change is still stored
        assertThat(sharedConfigurationFacade.getDomainSnapshot<TestSettings>("test")!!.settings.property).isEqualTo("other-admin")
    }

    @Test
    fun `should reject a write with an entity tag of an unknown domain state`() {
        // when: a write carries a tag that no state of the domain has ever had
        val result = sharedConfigurationFacade.write(ifMatch = "\"never-issued\"", property = "overwrite")

        // then: the precondition fails
        assertThat(result.error.status).isEqualTo(412)
    }

    @Test
    fun `should return the entity tag of the latest remote state to an instance that has not seen it`() {
        // given: two instances sharing the remote configuration, both loaded with the same state
        val configurationFacade = ConfigurationComponents().configurationFacade()
        val instanceA = remoteInstance(configurationFacade)
        val instanceB = remoteInstance(configurationFacade)

        // when: instance B stores a change and instance A reads the domain afterwards
        instanceB.updateDomain<TestSettings>("test", null) { TestSettings(property = "from-b").asSuccess() }
        val seenByA = instanceA.getDomainSnapshot<TestSettings>("test")!!

        // then: instance A reports the stored state, not its own cached copy
        assertThat(seenByA.settings.property).isEqualTo("from-b")
        assertThat(seenByA.etag).isEqualTo(instanceB.getDomainSnapshot<TestSettings>("test")!!.etag)
    }

    @Test
    fun `should reject a stale entity tag written by another instance sharing the remote configuration`() {
        // given: instance A has read the domain
        val configurationFacade = ConfigurationComponents().configurationFacade()
        val instanceA = remoteInstance(configurationFacade)
        val instanceB = remoteInstance(configurationFacade)
        val readByA = instanceA.getDomainSnapshot<TestSettings>("test")!!.etag

        // when: instance B stores a change, then instance A writes with the tag it read earlier
        instanceB.write(ifMatch = null, property = "from-b")
        val result = instanceA.write(ifMatch = readByA, property = "from-a")

        // then: the write is rejected against the latest stored state, and B's change survives
        assertThat(result.error.status).isEqualTo(412)
        assertThat(instanceB.getDomainSnapshot<TestSettings>("test")!!.settings.property).isEqualTo("from-b")
    }

}
