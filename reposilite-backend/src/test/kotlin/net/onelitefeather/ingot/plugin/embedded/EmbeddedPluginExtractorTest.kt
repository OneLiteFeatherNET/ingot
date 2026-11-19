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

package net.onelitefeather.ingot.plugin.embedded

import com.reposilite.plugin.fixtures.EmbeddedPluginFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

/**
 * The extraction directory of each test is a directory of its own temporary root, and the fallback root is that root too, so no
 * test touches the real temporary directory. Warnings and errors are collected in lists instead of being read from a console.
 */
internal class EmbeddedPluginExtractorTest {

    @TempDir
    lateinit var root: Path

    private val warnings = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private val working: Path by lazy { root.resolve("work").createDirectories() }
    private val embeddedDirectory: Path by lazy { working.resolve("embedded-plugins") }
    private val fallbackRoot: Path by lazy { root.resolve("tmp").createDirectories() }

    private val bytes = byteArrayOf(1, 2, 3, 4)

    private fun extractor() = EmbeddedPluginExtractor(
        embeddedPluginsDirectory = embeddedDirectory,
        fallbackRoot = fallbackRoot,
        warn = { warnings += it },
        error = { errors += it },
    )

    /** The embedded jars of a server jar that holds exactly the given entries. */
    private fun jarsOf(vararg entries: Pair<String, ByteArray>): List<EmbeddedJar> {
        val server = EmbeddedPluginFixture.serverJar(root.resolve("server"), mapOf(*entries.map { "embedded-plugins/${it.first}" to it.second }.toTypedArray()))
        return EmbeddedPluginSource(server).jars()
    }

    @Test
    fun `should create no directory when the server has no embedded jar`() {
        // when: nothing is extracted
        val extracted = extractor().extract(emptyList())

        // then: no directory is created and nothing is reported
        assertThat(embeddedDirectory.exists())
            .describedAs("the extraction directory without embedded jars")
            .isFalse()
        assertThat(extracted).describedAs("extracted jars").isEmpty()
        assertThat(warnings + errors).describedAs("warnings and errors").isEmpty()
    }

    @Test
    fun `should write each jar in full into the extraction directory`() {
        // given: one embedded jar
        val jars = jarsOf("alpha.jar" to bytes)

        // when: the jar is extracted
        val extracted = extractor().extract(jars)

        // then: the jar is written in full under its name and no partial file remains
        assertThat(extracted.single().path.readBytes())
            .describedAs("the extracted jar has the bytes of the entry")
            .isEqualTo(bytes)
        assertThat(embeddedDirectory.resolve("alpha.jar").exists())
            .describedAs("the jar under its final name")
            .isTrue()
        assertThat(embeddedDirectory.resolve("alpha.jar.partial").exists())
            .describedAs("no partial file after a successful write")
            .isFalse()
    }

    @Test
    fun `should remove a stale jar and a partial file left by an earlier start`() {
        // given: a stale jar and a partial file in the extraction directory from an earlier start
        embeddedDirectory.createDirectories()
        embeddedDirectory.resolve("old.jar").writeBytes(bytes)
        embeddedDirectory.resolve("alpha.jar.partial").writeBytes(bytes)

        // when: the server embeds only alpha.jar
        extractor().extract(jarsOf("alpha.jar" to bytes))

        // then: only the current jar is left in the directory
        assertThat(embeddedDirectory.resolve("old.jar").exists())
            .describedAs("a jar the server no longer embeds")
            .isFalse()
        assertThat(embeddedDirectory.resolve("alpha.jar.partial").exists())
            .describedAs("a partial file of an earlier crash")
            .isFalse()
        assertThat(embeddedDirectory.resolve("alpha.jar").readBytes())
            .describedAs("the current jar is written in full")
            .isEqualTo(bytes)
    }

    @Test
    fun `should remove the extraction directory when the server embeds no jar any more`() {
        // given: a jar from an earlier start
        embeddedDirectory.createDirectories()
        embeddedDirectory.resolve("old.jar").writeBytes(bytes)

        // when: the server has no embedded jar
        extractor().extract(emptyList())

        // then: the directory is removed
        assertThat(embeddedDirectory.exists())
            .describedAs("the extraction directory of a server without embedded jars")
            .isFalse()
    }

    @Test
    fun `should extract into the fallback directory and warn once when the extraction directory cannot be written`() {
        // given: a regular file that occupies the extraction directory, so it cannot be written
        working.resolve("embedded-plugins").writeBytes(bytes)

        // when: the jar is extracted
        val extracted = extractor().extract(jarsOf("alpha.jar" to bytes))

        // then: the jar is extracted into the fallback directory of the test root
        val fallback = EmbeddedPluginExtractor.fallbackDirectoryOf(fallbackRoot, embeddedDirectory)
        assertThat(extracted.single().path)
            .describedAs("the jar is extracted into the fallback directory")
            .isEqualTo(fallback.resolve("alpha.jar"))
        assertThat(fallback.resolve("alpha.jar").readBytes())
            .describedAs("the fallback copy has the bytes of the entry")
            .isEqualTo(bytes)
        assertThat(errors)
            .describedAs("no error when the fallback works")
            .isEmpty()

        // and: one warning names both directories
        assertThat(warnings)
            .describedAs("one warning for the fallback")
            .hasSize(1)
        assertThat(warnings.single())
            .describedAs("the warning names the extraction directory and the fallback directory")
            .contains(embeddedDirectory.toString())
            .contains(fallback.toString())
    }

    @Test
    fun `should log one error naming the jar and load nothing when neither directory can be written`() {
        // given: regular files that occupy the extraction directory and its fallback
        working.resolve("embedded-plugins").writeBytes(bytes)
        fallbackRoot.resolve(EmbeddedPluginExtractor.fallbackDirectoryOf(fallbackRoot, embeddedDirectory).fileName).writeBytes(bytes)

        // when: the jar is extracted
        val extracted = extractor().extract(jarsOf("alpha.jar" to bytes))

        // then: no jar is loaded, and one error names the jar
        assertThat(extracted)
            .describedAs("no jar is extracted when neither directory can be written")
            .isEmpty()
        assertThat(errors)
            .describedAs("one error for the jar")
            .hasSize(1)
        assertThat(errors.single())
            .describedAs("the error names the jar")
            .contains("alpha.jar")
    }

}
