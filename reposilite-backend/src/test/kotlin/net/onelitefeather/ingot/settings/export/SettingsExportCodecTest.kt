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

package net.onelitefeather.ingot.settings.export

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Base64

/**
 * The export codec runs with a fixed salt and nonce source and a small Argon2id cost, so each test is repeatable and fast.
 */
internal class SettingsExportCodecTest {

    private val mapper = ObjectMapper()
    private val password = "correct horse battery"
    private val createdAt = "2026-11-11T10:00:00Z"
    private val plain = """{"maven":{"s3":{"secretKey":"s3cret-plain-value"}}}"""
    private val codec = SettingsExportCodec(random = { size -> ByteArray(size) { 0x5a } }, cost = ExportCost(memoryKib = 8, iterations = 1, parallelism = 1))

    private fun sealed(): ObjectNode =
        codec.seal(plainDocument = plain, password = password, createdAt = createdAt)

    private fun ObjectNode.copyWith(block: ObjectNode.() -> Unit): JsonNode =
        deepCopy().apply(block)

    @Test
    fun `an export opens with its password to the plain document it was made from`() {
        // when
        val opened = codec.open(sealed(), password)

        // then
        assertThat(mapper.readTree(opened)).describedAs("opened document").isEqualTo(mapper.readTree(plain))
    }

    @Test
    fun `the export file holds the header fields and not the plain value`() {
        // when
        val file = sealed()

        // then
        assertThat(file.get("format").asText()).describedAs("format").isEqualTo("ingot-settings-export")
        assertThat(file.get("version").asInt()).describedAs("version").isEqualTo(1)
        assertThat(file.toString()).describedAs("export file").doesNotContain("s3cret-plain-value")
    }

    @Test
    fun `a wrong password fails with the generic message`() {
        // when / then
        val failure = assertThrows<SettingsExportException> { codec.open(sealed(), "another password") }
        assertThat(failure.message).describedAs("message").isEqualTo(SettingsExportCodec.REFUSED_MESSAGE)
    }

    @Test
    fun `a changed header field fails with the same generic message`() {
        // given: the createdAt field edited after the export was written
        val changed = sealed().copyWith { put("createdAt", "2030-01-01T00:00:00Z") }

        // when / then
        val failure = assertThrows<SettingsExportException> { codec.open(changed, password) }
        assertThat(failure.message).describedAs("message").isEqualTo(SettingsExportCodec.REFUSED_MESSAGE)
    }

    @Test
    fun `a changed ciphertext byte fails with the same generic message`() {
        // given: one byte of the ciphertext flipped
        val file = sealed()
        val bytes = Base64.getDecoder().decode(file.get("ciphertext").asText())
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        val changed = file.copyWith { put("ciphertext", Base64.getEncoder().encodeToString(bytes)) }

        // when / then
        val failure = assertThrows<SettingsExportException> { codec.open(changed, password) }
        assertThat(failure.message).describedAs("message").isEqualTo(SettingsExportCodec.REFUSED_MESSAGE)
    }

    @Test
    fun `an unknown version fails with the same generic message`() {
        // given
        val future = sealed().copyWith { put("version", 2) }

        // when / then
        val failure = assertThrows<SettingsExportException> { codec.open(future, password) }
        assertThat(failure.message).describedAs("message").isEqualTo(SettingsExportCodec.REFUSED_MESSAGE)
    }

    @Test
    fun `a memory cost above the bound fails with the same generic message and runs no derivation`() {
        // given: a declared memory cost above 262144 KiB
        val expensive = sealed().copyWith { (get("kdf") as ObjectNode).put("memoryKiB", 262145) }

        // when / then
        val failure = assertThrows<SettingsExportException> { codec.open(expensive, password) }
        assertThat(failure.message).describedAs("message").isEqualTo(SettingsExportCodec.REFUSED_MESSAGE)
    }

    @Test
    fun `a password shorter than twelve characters is refused when the export is made`() {
        // when / then
        val failure = assertThrows<SettingsExportException> {
            codec.seal(plainDocument = plain, password = "eleven-char", createdAt = createdAt)
        }
        assertThat(failure.message).describedAs("message").contains("12")
    }

}
