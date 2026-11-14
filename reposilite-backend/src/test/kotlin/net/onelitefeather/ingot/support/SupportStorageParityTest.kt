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

import com.reposilite.configuration.application.ConfigurationComponents
import com.reposilite.configuration.shared.SharedConfigurationFacade
import com.reposilite.configuration.shared.api.SharedSettings
import com.reposilite.configuration.shared.application.SharedConfigurationComponents
import com.reposilite.configuration.shared.SharedSettingsProvider
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.MavenSettings
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.plugin.Extensions
import com.reposilite.status.FailureFacade
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import panda.std.reactive.MutableReference
import panda.std.reactive.mutableReference
import java.io.File
import java.nio.file.Path

/**
 * Pins the storage entries of the support bundle for an S3 repository with a region and for a file system repository. The
 * entries are read through the storage mapping of the support plugin, as the bundle builds them.
 */
internal class SupportStorageParityTest {

    @TempDir
    lateinit var workingDirectory: File

    private fun sharedConfiguration(maven: MavenSettings): SharedConfigurationFacade {
        val logger = InMemoryLogger()
        val mavenSettings: MutableReference<SharedSettings> = mutableReference(maven)
        return SharedConfigurationComponents(
            journalist = logger,
            workingDirectory = workingDirectory.toPath(),
            extensions = Extensions(logger),
            sharedConfigurationPath = Path.of("shared.json"),
            failureFacade = FailureFacade(logger),
            configurationFacade = ConfigurationComponents().configurationFacade(),
        ).sharedConfigurationFacade(
            sharedSettingsProvider = SharedSettingsProvider(mapOf(MavenSettings::class.java to mavenSettings)),
        )
    }

    /** The storage mapping of the support plugin, which the bundle calls with the shared configuration. */
    @Suppress("UNCHECKED_CAST")
    private fun repositoryStorage(shared: SharedConfigurationFacade): List<RepositoryStorage> {
        val method = SupportPlugin::class.java.getDeclaredMethod("repositoryStorage", SharedConfigurationFacade::class.java)
        method.isAccessible = true
        return method.invoke(SupportPlugin(), shared) as List<RepositoryStorage>
    }

    @Test
    fun `an S3 repository entry carries its kind and its region`() {
        // given: an S3 repository with a region
        val shared = sharedConfiguration(
            MavenSettings(repositories = listOf(
                RepositorySettings(id = "archive", storageProvider = S3StorageProviderSettings(bucketName = "bucket", region = "eu-central-1")),
            ))
        )

        // when: the storage entries of the bundle are built
        val entries = repositoryStorage(shared)

        // then: the entry names the s3 kind and the region
        assertThat(entries).`as`("storage entries").containsExactly(RepositoryStorage("archive", "s3", "eu-central-1"))
    }

    @Test
    fun `a file system repository entry carries its kind and no region`() {
        // given: a file system repository
        val shared = sharedConfiguration(
            MavenSettings(repositories = listOf(
                RepositorySettings(id = "releases", storageProvider = FileSystemStorageProviderSettings(quota = "50%")),
            ))
        )

        // when: the storage entries of the bundle are built
        val entries = repositoryStorage(shared)

        // then: the entry names the fs kind and has no region
        assertThat(entries).`as`("storage entries").containsExactly(RepositoryStorage("releases", "fs", null))
    }

}
