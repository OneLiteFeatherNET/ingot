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

package com.reposilite.storage

import com.reposilite.ReposiliteObjectMapper.DEFAULT_OBJECT_MAPPER
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import com.reposilite.storage.s3.S3StorageProviderSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UnresolvedStorageProviderSettingsTest {

    private val unknownBlock = """{"type":"azure-blob","container":"releases","quota":1.10,"nested":{"b":2,"a":1}}"""

    @Test
    fun `should keep the raw block of a storage type that is not registered`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue(unknownBlock, StorageProviderSettings::class.java)

        assertThat(settings).describedAs("settings of an unregistered type").isInstanceOf(UnresolvedStorageProviderSettings::class.java)
        assertThat(settings.type).describedAs("type of the raw block").isEqualTo("azure-blob")
    }

    @Test
    fun `should write the raw block back with its field order and number text`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue(unknownBlock, StorageProviderSettings::class.java)

        assertThat(DEFAULT_OBJECT_MAPPER.writeValueAsString(settings))
            .describedAs("written document")
            .isEqualTo(unknownBlock)
    }

    @Test
    fun `should parse a repository with an unregistered storage type and keep the other repositories`() {
        val unknown = DEFAULT_OBJECT_MAPPER.readValue(
            """{"id":"plugin-repo","storageProvider":$unknownBlock}""",
            RepositorySettings::class.java,
        )
        val local = DEFAULT_OBJECT_MAPPER.readValue(
            """{"id":"local","storageProvider":{"type":"fs"}}""",
            RepositorySettings::class.java,
        )

        assertThat(unknown.storageProvider).describedAs("storage of the unregistered repository").isInstanceOf(UnresolvedStorageProviderSettings::class.java)
        assertThat(local.storageProvider).describedAs("storage of the local repository").isInstanceOf(FileSystemStorageProviderSettings::class.java)
    }

    @Test
    fun `should guess the s3 type for a block without type that has a bucket name`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue("""{"bucketName":"releases"}""", StorageProviderSettings::class.java)

        assertThat(settings).describedAs("guessed settings").isInstanceOf(S3StorageProviderSettings::class.java)
    }

    @Test
    fun `should guess the fs type for a block without type and without a bucket name`() {
        val settings = DEFAULT_OBJECT_MAPPER.readValue("{}", StorageProviderSettings::class.java)

        assertThat(settings).describedAs("guessed settings").isInstanceOf(FileSystemStorageProviderSettings::class.java)
    }

}
