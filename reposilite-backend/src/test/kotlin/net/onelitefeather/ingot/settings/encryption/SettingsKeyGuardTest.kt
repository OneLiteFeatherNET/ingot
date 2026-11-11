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

package net.onelitefeather.ingot.settings.encryption

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

/**
 * The start decision is a pure function of the configured keys, the stored key record and the sealed values of the stored
 * document. Each test passes its own inputs, so no test depends on the storage of another.
 */
internal class SettingsKeyGuardTest {

    private val key = ByteArray(32) { (it * 3 + 9).toByte() }
    private val previousKey = ByteArray(32) { (it * 5 + 2).toByte() }
    private val otherKey = ByteArray(32) { (it * 11 + 4).toByte() }

    private val active = SettingsKeyConfiguration(active = key, previous = null, mode = SettingsEncryptionMode.OPTIONAL)
    private fun codec(key: ByteArray) = SettingsEnvelopeCodec(key = key, nonces = NonceSource { ByteArray(12) })
    private val storedForKey = StoredSettingsKey.of(codec(key))

    private fun sealedBy(key: ByteArray, domain: String = "maven", path: String = "s3.secretKey") =
        SealedSetting(domain = domain, path = path, keyId = codec(key).keyId)

    @Test
    fun `a start without a stored record and without sealed values adopts the configured key`() {
        // when
        val outcome = SettingsKeyGuard.verify(active, stored = null, sealed = emptyList())

        // then: the record of the configured key is to be stored
        assertThat(outcome).describedAs("outcome").isEqualTo(KeyStartOutcome.Adopt(StoredSettingsKey.of(codec(key))))
    }

    @Test
    fun `a matching stored record starts without a warning`() {
        // when
        val outcome = SettingsKeyGuard.verify(active, stored = storedForKey, sealed = listOf(sealedBy(key)))

        // then
        assertThat(outcome).describedAs("outcome").isEqualTo(KeyStartOutcome.Accepted)
    }

    @Test
    fun `the record of the previous key starts with a warning that a rotation is open`() {
        // given: the database holds the record of the previous key, the instance has the new key active
        val rotating = SettingsKeyConfiguration(active = key, previous = previousKey, mode = SettingsEncryptionMode.OPTIONAL)
        val storedPrevious = StoredSettingsKey.of(codec(previousKey))

        // when
        val outcome = SettingsKeyGuard.verify(rotating, stored = storedPrevious, sealed = emptyList())

        // then
        assertThat(outcome).describedAs("outcome").isEqualTo(KeyStartOutcome.AcceptedPreviousKey(storedPrevious))
    }

    @Test
    fun `another stored record stops the start and the message names only the stored key identifier`() {
        // given: the database belongs to a key that this instance does not have
        val stored = StoredSettingsKey.of(codec(otherKey))

        // when / then
        val failure = assertThrows<IllegalStateException> {
            SettingsKeyGuard.verify(active, stored = stored, sealed = listOf(sealedBy(otherKey)))
        }
        assertThat(failure.message).describedAs("message").contains(stored.keyId)
        assertThat(failure.message).describedAs("message").doesNotContain(Base64.getEncoder().encodeToString(otherKey))
        assertThat(failure.message).describedAs("message").doesNotContain(Base64.getEncoder().encodeToString(key))
    }

    @Test
    fun `sealed values without a stored record that another key sealed stop the start`() {
        // given: an envelope of another key and no record stored yet
        val failure = assertThrows<IllegalStateException> {
            SettingsKeyGuard.verify(active, stored = null, sealed = listOf(sealedBy(otherKey)))
        }

        // then: the message names the key identifier of the envelope
        assertThat(failure.message).describedAs("message").contains(codec(otherKey).keyId)
    }

    @Test
    fun `sealed values without a configured key stop the start and the message names the domain and the property`() {
        // given: an unkeyed instance and a document with an envelope
        val unkeyed = SettingsKeyConfiguration(active = null, previous = null, mode = SettingsEncryptionMode.OPTIONAL)

        // when / then
        val failure = assertThrows<IllegalStateException> {
            SettingsKeyGuard.verify(unkeyed, stored = null, sealed = listOf(sealedBy(key, domain = "maven", path = "s3.secretKey")))
        }
        assertThat(failure.message).describedAs("message").contains("maven").contains("s3.secretKey")
    }

    @Test
    fun `no key and no sealed values leave the instance unkeyed`() {
        // given: a plain document and no key
        val unkeyed = SettingsKeyConfiguration(active = null, previous = null, mode = SettingsEncryptionMode.OPTIONAL)

        // when
        val outcome = SettingsKeyGuard.verify(unkeyed, stored = null, sealed = emptyList())

        // then: nothing is stored and nothing is checked
        assertThat(outcome).describedAs("outcome").isEqualTo(KeyStartOutcome.Unkeyed)
    }

    @Test
    fun `required mode without a key stops the start and the message names the variables`() {
        // given
        val required = SettingsKeyConfiguration(active = null, previous = null, mode = SettingsEncryptionMode.REQUIRED)

        // when / then
        val failure = assertThrows<IllegalStateException> {
            SettingsKeyGuard.verify(required, stored = null, sealed = emptyList())
        }
        assertThat(failure.message).describedAs("message").contains("INGOT_SETTINGS_KEY").contains("INGOT_SETTINGS_KEY_FILE")
    }

    @Test
    fun `the stored record round trips through its text form`() {
        // given
        val record = StoredSettingsKey.of(codec(key))

        // when / then
        assertThat(StoredSettingsKey.parse(record.serialize())).describedAs("parsed record").isEqualTo(record)
        assertThat(StoredSettingsKey.parse("")).describedAs("empty record").isNull()
    }

}
