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

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.plugin.fixtures.FixturePluginOne
import com.reposilite.plugin.fixtures.FixturePluginTwo
import com.reposilite.plugin.fixtures.PluginJarFixture
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class PluginIdCollisionTest {

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @TempDir
    lateinit var pluginsDirectory: Path

    @Test
    fun `should stop start-up when two plugin jars declare the same plugin id`() {
        // given: two jars in the plugins directory, each declaring a plugin with the id "fixture"
        PluginJarFixture.write(pluginsDirectory, "first.jar", FixturePluginOne::class)
        PluginJarFixture.write(pluginsDirectory, "second.jar", FixturePluginTwo::class)
        val extensions = Extensions(journalist)
        val loader = PluginLoader(pluginsDirectory, extensions)

        // when: the plugins directory is loaded
        // then: the second registration is refused and the server does not start
        assertThatThrownBy { loader.loadPluginsByServiceFiles() }
            .describedAs("a duplicate plugin id must stop start-up with the registry message")
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Plugin fixture is already registered")
    }

    @Test
    fun `should register a single plugin when the id is unique`() {
        // given: one jar with a plugin id that no other jar declares
        PluginJarFixture.write(pluginsDirectory, "only.jar", FixturePluginOne::class)
        val extensions = Extensions(journalist)

        // when: the plugins directory is loaded
        PluginLoader(pluginsDirectory, extensions).loadPluginsByServiceFiles()

        // then: the jar's plugin is registered under its id
        assertThat(extensions.getPlugins()["fixture"]?.plugin)
            .describedAs("the jar must register its plugin under the id fixture")
            .isInstanceOf(FixturePluginOne::class.java)
    }

}
