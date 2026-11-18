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

package com.reposilite.plugin

import com.reposilite.journalist.Channel
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.api.Facade
import com.reposilite.plugin.api.Plugin
import com.reposilite.plugin.api.ReposilitePlugin
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The description of a registered plugin and its start warning. The log is read from memory, never from the console. */
internal class PluginDescriptionTest {

    @Plugin(name = "checksum", description = "Verifies checksums of uploaded artifacts")
    class DescribedPlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "plain")
    class PlainPlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    @Plugin(name = "verbose", description = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")
    class VerbosePlugin : ReposilitePlugin() {
        override fun initialize(): Facade? = null
    }

    private val buffer = ByteArrayOutputStream()
    private val stream = PrintStream(buffer, true, UTF_8)
    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(stream, stream, Channel.WARN, true)
    }

    /** The captured logger writes only from the warning channel up, so every captured line is a warning. */
    private fun capturedWarnings(): List<String> {
        stream.flush()
        return buffer.toString(UTF_8).lines().filter { it.isNotEmpty() }
    }

    @Test
    fun `should read the declared description of a registered plugin`() {
        // given: a plugin that declares a description
        val extensions = Extensions(journalist)
        extensions.registerPlugin(DescribedPlugin())

        // when: the description is read by name
        val description = extensions.descriptionOf("checksum")

        // then: the declared text is returned
        assertThat(description)
            .describedAs("description of a plugin that declares one")
            .isEqualTo("Verifies checksums of uploaded artifacts")
    }

    @Test
    fun `should read the empty text for a plugin that declares no description`() {
        // given: a plugin that does not set the element
        val extensions = Extensions(journalist)
        extensions.registerPlugin(PlainPlugin())

        // when: the description is read by name
        val description = extensions.descriptionOf("plain")

        // then: the empty text, which means no description
        assertThat(description)
            .describedAs("description of a plugin without the element")
            .isEmpty()
    }

    @Test
    fun `should read the empty text for a name that is not registered`() {
        // when: the description of an unknown name is read
        val description = Extensions(journalist).descriptionOf("missing")

        // then: the lookup does not fail and reads the empty text
        assertThat(description)
            .describedAs("description of an unknown plugin name")
            .isEmpty()
    }

    @Test
    fun `should log one warning that names the plugin when its description is cut`() {
        // given: a plugin whose description is 300 characters long
        val extensions = Extensions(journalist)

        // when: the plugin is registered
        extensions.registerPlugin(VerbosePlugin())

        // then: exactly one warning line names the plugin and the limit
        val warnings = capturedWarnings()
        assertThat(warnings)
            .describedAs("warning lines of a cut description")
            .hasSize(1)
        assertThat(warnings.single())
            .describedAs("the warning names the plugin and the limit")
            .contains("verbose")
            .contains("280")
    }

    @Test
    fun `should log no warning when the description fits the limit`() {
        // given: a plugin with a short description
        val extensions = Extensions(journalist)

        // when: the plugin is registered
        extensions.registerPlugin(DescribedPlugin())

        // then: no warning is written
        assertThat(capturedWarnings())
            .describedAs("warning lines of a short description")
            .isEmpty()
    }

    @Test
    fun `should default the description element of the annotation to the empty text`() {
        // when: the default value is read from the annotation type itself
        val default = Plugin::class.java.getMethod("description").defaultValue

        // then: the default is the empty text, the value of every plugin that declares nothing
        assertThat(default)
            .describedAs("default of Plugin.description")
            .isEqualTo("")
    }

}
