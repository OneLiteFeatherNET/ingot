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

package com.reposilite.shared

import com.reposilite.configuration.InterleavingConfigurationRepository
import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.infrastructure.InMemoryConfigurationRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.security.MessageDigest

internal class EntityTagSignerTest {

    private val content = """{"property":"value"}""".toByteArray()

    @Test
    fun `should derive a quoted strong entity tag from the content`() {
        // when: a tag is derived from some content
        val tag = EntityTagSigner.ephemeral().of(content)

        // then: it is a strong entity tag, quoted as HTTP requires
        assertThat(tag).matches("\"[0-9a-f]{64}\"")
    }

    @Test
    fun `should derive the same entity tag for identical content under one key`() {
        val signer = EntityTagSigner.ephemeral()

        assertThat(signer.of("same".toByteArray())).isEqualTo(signer.of("same".toByteArray()))
    }

    @Test
    fun `should derive a different entity tag when the content changes`() {
        val signer = EntityTagSigner.ephemeral()

        assertThat(signer.of("before".toByteArray())).isNotEqualTo(signer.of("after".toByteArray()))
    }

    @Test
    fun `should not derive the entity tag as the plain sha-256 of the content`() {
        // given: the tag an attacker could compute offline without the server key
        val unkeyed = MessageDigest.getInstance("SHA-256").digest(content).joinToString(separator = "") { "%02x".format(it) }

        // when: the server derives the tag of the same content
        val tag = EntityTagSigner.ephemeral().of(content)

        // then: the keyed tag is not that value
        assertThat(tag).isNotEqualTo("\"$unkeyed\"")
    }

    @Test
    fun `should derive a different entity tag for the same content under another key`() {
        // given: two instances with independent keys
        val first = EntityTagSigner.ephemeral()
        val second = EntityTagSigner.ephemeral()

        // then: the same content does not yield the same tag
        assertThat(first.of(content)).isNotEqualTo(second.of(content))
    }

    @Test
    fun `should derive the same entity tag from signers that read the key from one store`() {
        // given: a store that already holds a key from an earlier start
        val store = ConfigurationComponents().configurationFacade()
        EntityTagSigner.stored(store)

        // when: two later instances start on that store
        val first = EntityTagSigner.stored(store)
        val second = EntityTagSigner.stored(store)

        // then: they derive identical tags for identical content
        assertThat(first.of(content)).isEqualTo(second.of(content))
    }

    @Test
    fun `should agree on one key when two instances create it at the same time`() {
        // given: a store where instance B creates the key in the moment between instance A's lookup and A's insert
        val repository = InterleavingConfigurationRepository(InMemoryConfigurationRepository())
        val store = ConfigurationComponents().configurationFacade(configurationRepository = repository)
        var instanceB: EntityTagSigner? = null
        repository.beforeNextSaveIfAbsent { instanceB = EntityTagSigner.stored(store) }

        // when: instance A starts
        val instanceA = EntityTagSigner.stored(store)

        // then: A lost the insert and reads back the key B stored, so both derive the same tag
        assertThat(instanceB).isNotNull
        assertThat(instanceA.of(content)).isEqualTo(instanceB!!.of(content))
    }

}
