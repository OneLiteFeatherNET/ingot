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

package com.reposilite.maven

import com.reposilite.journalist.Journalist
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.StorageProviderSettings
import net.onelitefeather.ingot.repository.RepositoryCacheCoordinator
import net.onelitefeather.ingot.storage.capability.ClusterBus
import net.onelitefeather.ingot.storage.capability.ClusterCacheAware
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The cluster bus binds every repository whose storage declares [ClusterCacheAware], and no other. Core asks the capability, not the
 * class of the storage, so a storage of a plugin is bound the same way as the S3 storage.
 */
internal class ClusterCacheCapabilityTest : MavenSpecification() {

    private data class BindCall(val bus: ClusterBus, val maxAge: Duration)

    private val bus = object : ClusterBus {}
    private val binds = CopyOnWriteArrayList<BindCall>()

    /** Declares the cluster capability and records the binds it receives. */
    private inner class RecordingClusterStorage(private val delegate: StorageProvider) : StorageProvider by delegate, ClusterCacheAware {

        override fun bind(clusterBus: ClusterBus, maxAge: Duration) {
            binds += BindCall(clusterBus, maxAge)
        }

        override fun invalidate(keys: Collection<String>) {}

    }

    override fun repositories(): List<RepositorySettings> = listOf(
        RepositorySettings(id = "aware"),
        RepositorySettings(id = "plain"),
    )

    override fun storageFacade(): StorageFacade = object : StorageFacade() {

        override fun createStorageProvider(
            journalist: Journalist,
            failureFacade: FailureFacade,
            workingDirectory: Path,
            repository: String,
            storageSettings: StorageProviderSettings,
        ): StorageProvider? =
            super.createStorageProvider(journalist, failureFacade, workingDirectory, repository, storageSettings)
                ?.let { storage -> if (repository == "aware") RecordingClusterStorage(storage) else storage }

    }

    @Test
    fun `a storage that declares the cluster capability is bound once with the default maximum age`() {
        // when: the cluster bus opens
        (mavenFacade.repositoryRegistry() as RepositoryCacheCoordinator).bindToClusterBus(bus)

        // then: only the aware storage is bound, with the bus and the default maximum age of 300 seconds
        assertThat(binds).`as`("binds received by the aware storage").containsExactly(BindCall(bus, Duration.ofSeconds(300)))
    }

}
