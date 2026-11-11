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
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64

/**
 * The key sources are read from an injected environment map and from files in a temporary directory. No test reads the process
 * environment or a file outside its own directory.
 */
internal class SettingsKeySourcesTest {

    @TempDir
    lateinit var directory: Path

    private val key = ByteArray(32) { (it * 3 + 9).toByte() }
    private val otherKey = ByteArray(32) { (it * 5 + 2).toByte() }
    private val encoded = Base64.getEncoder().encodeToString(key)

    private fun keyFile(name: String, content: String): Path =
        Files.writeString(directory.resolve(name), content)

    @Test
    fun `the key is read from INGOT_SETTINGS_KEY`() {
        // when: only the inline key is set
        val configuration = SettingsKeySources.read(mapOf("INGOT_SETTINGS_KEY" to encoded))

        // then: the decoded key is the active key
        assertThat(configuration.active).describedAs("active key").isEqualTo(key)
    }

    @Test
    fun `the key is read from the file named by INGOT_SETTINGS_KEY_FILE when it ends with one line break`() {
        // given: a key file that holds the key and one trailing line break
        val file = keyFile("settings.key", "$encoded\n")

        // when
        val configuration = SettingsKeySources.read(mapOf("INGOT_SETTINGS_KEY_FILE" to file.toString()))

        // then
        assertThat(configuration.active).describedAs("active key").isEqualTo(key)
    }

    @Test
    fun `the REPOSILITE spelling is read when the INGOT name is absent`() {
        // when
        val configuration = SettingsKeySources.read(mapOf("REPOSILITE_SETTINGS_KEY" to encoded))

        // then
        assertThat(configuration.active).describedAs("active key").isEqualTo(key)
    }

    @Test
    fun `the INGOT name wins over the REPOSILITE spelling when both are set`() {
        // given: two different keys under the two spellings
        val legacy = Base64.getEncoder().encodeToString(otherKey)

        // when
        val configuration = SettingsKeySources.read(
            mapOf("INGOT_SETTINGS_KEY" to encoded, "REPOSILITE_SETTINGS_KEY" to legacy)
        )

        // then: the INGOT key is used and the legacy one is ignored
        assertThat(configuration.active).describedAs("active key").isEqualTo(key)
    }

    @Test
    fun `setting the inline key and the key file stops the start and names both variables`() {
        // given: both sources set
        val file = keyFile("settings.key", encoded)

        // when / then
        val failure = assertThrows<IllegalArgumentException> {
            SettingsKeySources.read(mapOf("INGOT_SETTINGS_KEY" to encoded, "INGOT_SETTINGS_KEY_FILE" to file.toString()))
        }
        assertThat(failure.message).describedAs("message").contains("INGOT_SETTINGS_KEY").contains("INGOT_SETTINGS_KEY_FILE")
        assertThat(failure.message).describedAs("message").doesNotContain(encoded)
    }

    @Test
    fun `a key of 16 bytes stops the start and the message names the variable and the length but not the value`() {
        // given: a base64 value that decodes to 16 bytes
        val short = Base64.getEncoder().encodeToString(ByteArray(16) { 0x41 })

        // when / then
        val failure = assertThrows<IllegalArgumentException> {
            SettingsKeySources.read(mapOf("INGOT_SETTINGS_KEY" to short))
        }
        assertThat(failure.message).describedAs("message").contains("INGOT_SETTINGS_KEY").contains("16")
        assertThat(failure.message).describedAs("message").doesNotContain(short)
    }

    @Test
    fun `a value that is not base64 stops the start and the message holds no part of the value`() {
        // given: a value that is not base64
        val broken = "not base64 at all!"

        // when / then
        val failure = assertThrows<IllegalArgumentException> {
            SettingsKeySources.read(mapOf("INGOT_SETTINGS_KEY" to broken))
        }
        assertThat(failure.message).describedAs("message").contains("INGOT_SETTINGS_KEY")
        assertThat(failure.message).describedAs("message").doesNotContain("base64 at all")
    }

    @Test
    fun `the previous key is read from its own variables`() {
        // given: the previous key in a file
        val file = keyFile("previous.key", Base64.getEncoder().encodeToString(otherKey) + "\n")

        // when
        val configuration = SettingsKeySources.read(
            mapOf("INGOT_SETTINGS_KEY" to encoded, "INGOT_SETTINGS_KEY_PREVIOUS_FILE" to file.toString())
        )

        // then: the active key is the inline one and the previous key is the file one
        assertThat(configuration.active).describedAs("active key").isEqualTo(key)
        assertThat(configuration.previous).describedAs("previous key").isEqualTo(otherKey)
    }

    @Test
    fun `without any key variable there is no key and the mode is optional`() {
        // when
        val configuration = SettingsKeySources.read(emptyMap())

        // then
        assertThat(configuration.active).describedAs("active key").isNull()
        assertThat(configuration.previous).describedAs("previous key").isNull()
        assertThat(configuration.mode).describedAs("mode").isEqualTo(SettingsEncryptionMode.OPTIONAL)
    }

    @Test
    fun `the mode is read from INGOT_SETTINGS_ENCRYPTION`() {
        val configuration = SettingsKeySources.read(mapOf("INGOT_SETTINGS_ENCRYPTION" to "required"))

        assertThat(configuration.mode).describedAs("mode").isEqualTo(SettingsEncryptionMode.REQUIRED)
    }

    @Test
    fun `a mode other than optional or required stops the start`() {
        assertThrows<IllegalArgumentException> {
            SettingsKeySources.read(mapOf("INGOT_SETTINGS_ENCRYPTION" to "sometimes"))
        }
    }

}
