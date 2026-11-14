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

package net.onelitefeather.ingot.plugin.store

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

internal class PluginStoreFactoryTest {

    @TempDir
    lateinit var dataDirectory: Path

    private val journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    @Test
    fun `should create no store when nothing is configured`() {
        // when: the store is created from an empty configuration
        val store = PluginStoreFactory.create(emptyMap(), localSelection = "", dataDirectory = dataDirectory, journalist = journalist)

        // then: there is no store, and the data directory gets no cache
        assertThat(store).describedAs("an installation without configuration must have no store").isNull()
        assertThat(dataDirectory.resolve("plugin-cache")).describedAs("no cache directory may be created").doesNotExist()
    }

    @Test
    fun `should create no store when the recovery switch is on`() {
        // when: a selection is configured, and the recovery switch skips the store for this start
        val store = PluginStoreFactory.create(
            mapOf("INGOT_PLUGINS" to "org.example:search:2.1.0", "INGOT_PLUGIN_STORE_DISABLED" to "true"),
            localSelection = "",
            dataDirectory = dataDirectory,
            journalist = journalist,
        )

        // then: there is no store for this start
        assertThat(store).describedAs("the recovery switch must skip the store").isNull()
    }

    @Test
    fun `should create a store when a plugin is selected`() {
        // when: a plugin is selected through the local key
        val store = PluginStoreFactory.create(emptyMap(), localSelection = "org.example:search:2.1.0", dataDirectory = dataDirectory, journalist = journalist)

        // then: the store exists, and creating it has not touched the cache yet
        assertThat(store).describedAs("a selected plugin must activate the store").isNotNull
        assertThat(dataDirectory.resolve("plugin-cache")).describedAs("the cache is created only when a jar is written").doesNotExist()
    }

}
