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

package net.onelitefeather.ingot.support

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.util.Random
import java.util.zip.ZipInputStream

internal class SupportBundleWriterTest {

    private fun entriesOf(zip: ByteArray): Map<String, String> {
        val entries = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                entries[entry.name] = String(input.readBytes())
            }
        }
        return entries
    }

    @Test
    fun `should write every given file as a zip entry under its path`() {
        // given: two files, one of them in a folder
        val files = linkedMapOf(
            "manifest.json" to "{}".toByteArray(),
            "logs/ingot-test.log" to "boot finished".toByteArray(),
        )

        // when: the zip is written
        val entries = entriesOf(SupportBundleWriter.write(files))

        // then: both entries are there with their content
        assertThat(entries).`as`("entries of the zip").containsExactlyEntriesOf(mapOf(
            "manifest.json" to "{}",
            "logs/ingot-test.log" to "boot finished",
        ))
    }

    @Test
    fun `should refuse a bundle above the eight mebibyte cap`() {
        // given: content that does not compress, larger than the cap
        val random = Random(7)
        val noise = ByteArray(SupportBundleWriter.MAX_BYTES.toInt() + 1024 * 1024).also { random.nextBytes(it) }

        // when: the zip is written
        // then: the writer refuses it, so no part of the bundle is served
        assertThatThrownBy { SupportBundleWriter.write(mapOf("logs/noise.log" to noise)) }
            .`as`("bundle above the cap")
            .isInstanceOf(BundleTooLargeException::class.java)
    }

    @Test
    fun `should cap the bundle at eight mebibytes`() {
        assertThat(SupportBundleWriter.MAX_BYTES).`as`("cap of the bundle").isEqualTo(8L * 1024 * 1024)
    }
}
