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

import com.reposilite.maven.application.RepositorySettings
import com.reposilite.maven.specification.MavenSpecification
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.io.path.fileStore

private const val TIMEOUT_SECONDS = 10L

/**
 * Pins the usage snapshot of file system repositories with a percentage quota and with a fixed quota, as the maven facade
 * reports it today.
 */
internal class FileSystemUsageParityTest : MavenSpecification() {

    override fun repositories(): List<RepositorySettings> = listOf(
        RepositorySettings(id = "percent", storageProvider = FileSystemStorageProviderSettings(quota = "50%")),
        RepositorySettings(id = "fixed", storageProvider = FileSystemStorageProviderSettings(quota = "10MB")),
    )

    @Test
    fun `a percentage quota is half the total capacity of the disk of the repository`() {
        // given: a file in a repository with a 50% quota
        addFileToRepository(FileSpec("percent", "com/app/1.0/app-1.0.jar", "12345"))

        // when: usage is measured
        val usage = mavenFacade.refreshStorageUsage().get(TIMEOUT_SECONDS, SECONDS)

        // then: the figures and the quota in bytes are the ones of the file system repository
        val percent = usage.repositories.single { it.id == "percent" }
        val totalSpace = workingDirectory.toPath().fileStore().totalSpace
        assertThat(percent.quota).`as`("configured quota").isEqualTo("50%")
        assertThat(percent.quotaBytes).`as`("quota bytes of a 50% quota").isEqualTo((totalSpace * 0.5).toLong())
        assertThat(percent.usedBytes).`as`("used bytes").isEqualTo(5L)
        assertThat(percent.fileCount).`as`("file count").isEqualTo(1L)
        assertThat(percent.storageType).`as`("storage type").isEqualTo("fs")
        assertThat(percent.group).`as`("group flag").isFalse
    }

    @Test
    fun `a fixed quota is read as the size written in the settings`() {
        // when: usage is measured
        val usage = mavenFacade.refreshStorageUsage().get(TIMEOUT_SECONDS, SECONDS)

        // then: the fixed quota is 10 MB in bytes and the storage type is fs
        val fixed = usage.repositories.single { it.id == "fixed" }
        assertThat(fixed.quota).`as`("configured quota").isEqualTo("10MB")
        assertThat(fixed.quotaBytes).`as`("quota bytes of a 10MB quota").isEqualTo(10L * 1024 * 1024)
        assertThat(fixed.storageType).`as`("storage type").isEqualTo("fs")
    }

}
