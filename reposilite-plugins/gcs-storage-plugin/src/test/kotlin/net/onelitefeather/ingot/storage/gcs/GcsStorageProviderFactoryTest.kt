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

@file:OptIn(net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.gcs

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageEntrySource
import com.reposilite.storage.StorageProviderFactory
import net.onelitefeather.ingot.storage.STORAGE_PROVIDER_API_VERSION
import net.onelitefeather.ingot.storage.StorageProviderContext
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.ServiceLoader

/** The factory of the `gcs` type: its identity, its refusals, and the service file that lets the server find it. */
internal class GcsStorageProviderFactoryTest {

    @TempDir
    lateinit var workingDirectory: Path

    private fun context(): StorageProviderContext {
        val logger: Journalist = InMemoryLogger()
        val root = workingDirectory
        return object : StorageProviderContext {
            override val journalist: Journalist = logger
            override val failureFacade: FailureFacade = FailureFacade(logger)
            override val workingDirectory: Path = root
            override val repositoryName: String = "repository"
        }
    }

    private val factory = GcsStorageProviderFactory()

    @Test
    fun `should declare the gcs type and the current storage provider API version`() {
        // then: the factory identifies itself as the gcs type and the version this server accepts
        assertThat(factory.type).`as`("type").isEqualTo("gcs")
        assertThat(factory.apiVersion).`as`("API version").isEqualTo(STORAGE_PROVIDER_API_VERSION)
        assertThat(factory.settingsType).`as`("settings type").isEqualTo(GcsStorageProviderSettings::class.java)
    }

    @Test
    fun `should create a provider with the native listing for a valid repository`() {
        // when: a repository is built from its settings
        val provider = factory.create(context(), GcsStorageProviderSettings(bucket = "repositories"))

        // then: the provider exists and lists natively
        assertThat(provider).`as`("native entry source").isInstanceOf(StorageEntrySource::class.java)
    }

    @Test
    fun `should refuse settings that fail validation and name the key`() {
        // then: the creation fails with the message of the rule, which names the bucket
        assertThatThrownBy { factory.create(context(), GcsStorageProviderSettings(bucket = "")) }
            .hasMessageContaining("bucket")
    }

    @Test
    fun `should refuse an endpoint while the emulator switch is off and name the variable`() {
        // then: the creation fails and names the switch that would allow the endpoint
        assertThatThrownBy {
            factory.create(context(), GcsStorageProviderSettings(bucket = "repositories", endpoint = "http://localhost:4443"))
        }.hasMessageContaining("INGOT_GCS_ALLOW_EMULATOR_ENDPOINT")
    }

    @Test
    fun `should accept an endpoint when the factory is built with the emulator allowed`() {
        // when: the emulator is allowed and an endpoint is set
        val provider = GcsStorageProviderFactory(emulatorEndpointAllowed = true)
            .create(context(), GcsStorageProviderSettings(bucket = "repositories", endpoint = "http://localhost:4443"))

        // then: the repository is built without contacting the emulator
        assertThat(provider).isNotNull()
    }

    @Test
    fun `should be found through the storage provider service file of the jar`() {
        // when: the storage provider factories of the class path are listed as the loader lists them
        val factories = ServiceLoader.load(StorageProviderFactory::class.java).map { it.javaClass.name }

        // then: the gcs factory is among them
        assertThat(factories).`as`("registered factories").contains(GcsStorageProviderFactory::class.java.name)
    }

}
