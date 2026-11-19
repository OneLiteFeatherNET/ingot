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
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

internal class EmbeddedPluginSourceTest {

    @TempDir
    lateinit var root: Path

    private val bytes = byteArrayOf(7, 8, 9)

    @Test
    fun `should list the jars that a server jar holds under embedded-plugins`() {
        // given: a server jar with direct jar entries and entries that must be ignored
        val server = EmbeddedPluginFixture.serverJar(
            root.resolve("server"),
            mapOf(
                "embedded-plugins/alpha.jar" to bytes,
                "embedded-plugins/beta.jar" to bytes,
                "embedded-plugins/readme.txt" to bytes,
                "embedded-plugins/nested/gamma.jar" to bytes,
                "other/delta.jar" to bytes,
            ),
        )

        // when: the embedded jars are listed
        val names = EmbeddedPluginSource(server).jars().map { it.fileName }

        // then: only the direct jar entries under embedded-plugins are listed, each once
        assertThat(names)
            .describedAs("the jars under embedded-plugins of the server jar")
            .containsExactlyInAnyOrder("alpha.jar", "beta.jar")
    }

    @Test
    fun `should list the jars that a classes directory holds under embedded-plugins`() {
        // given: a classes directory with jars under embedded-plugins and a file that is not a jar
        val classes = root.resolve("classes").createDirectories()
        classes.resolve("embedded-plugins").createDirectories()
        classes.resolve("embedded-plugins").resolve("alpha.jar").writeBytes(bytes)
        classes.resolve("embedded-plugins").resolve("readme.txt").writeBytes(bytes)

        // when: the embedded jars are listed
        val names = EmbeddedPluginSource(classes).jars().map { it.fileName }

        // then: the jar of the directory is listed and the other file is not
        assertThat(names)
            .describedAs("the jars under embedded-plugins of the classes directory")
            .containsExactly("alpha.jar")
    }

    @Test
    fun `should copy an embedded jar in full, as it is`() {
        // given: a server jar with one embedded jar
        val server = EmbeddedPluginFixture.serverJar(root.resolve("server"), mapOf("embedded-plugins/alpha.jar" to bytes))

        // when: the embedded jar is copied to a target
        val target = root.resolve("copy").createDirectories().resolve("alpha.jar")
        EmbeddedPluginSource(server).jars().single().writeTo(target)

        // then: the copy has the same bytes as the entry
        assertThat(target.readBytes())
            .describedAs("the copy of the embedded jar")
            .isEqualTo(bytes)
    }

    @Test
    fun `should list no jar when the server jar has no embedded entry`() {
        // given: a server jar without any embedded-plugins entry
        val server = EmbeddedPluginFixture.serverJar(root.resolve("server"), mapOf("com/example/Main.class" to bytes))

        // when: the embedded jars are listed
        val jars = EmbeddedPluginSource(server).jars()

        // then: the list is empty
        assertThat(jars)
            .describedAs("a server jar without embedded entries")
            .isEmpty()
    }

    @Test
    fun `should list no jar when the code source does not exist`() {
        // when: the embedded jars are listed for a code source that is missing
        val jars = EmbeddedPluginSource(root.resolve("missing.jar")).jars()

        // then: the list is empty and nothing fails
        assertThat(jars)
            .describedAs("a missing code source")
            .isEmpty()
    }

}
