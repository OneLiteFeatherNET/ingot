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

@file:OptIn(ExperimentalIngotStorageApi::class)

package net.onelitefeather.ingot.storage.cache

import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.PrintStreamLogger
import com.reposilite.status.FailureFacade
import com.reposilite.storage.StorageProvider
import com.reposilite.storage.api.Location
import com.reposilite.storage.filesystem.FileSystemStorageProviderFactory
import com.reposilite.storage.filesystem.FileSystemStorageProviderSettings
import net.onelitefeather.ingot.storage.ExperimentalIngotStorageApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * The storage contract for the cache, as a differential check: the same scenario runs against a plain file system provider and against
 * a cache over a second file system provider, and both must produce the same observations. Every case the plain provider passes, the
 * cache passes as well.
 */
class CacheStorageContractTest {

    @TempDir
    lateinit var root: Path

    private val journalist: Journalist = object : Journalist {
        override fun getLogger(): Logger = PrintStreamLogger(System.out, System.err)
    }

    private fun fileSystem(directory: Path): StorageProvider =
        FileSystemStorageProviderFactory().create(
            journalist = journalist,
            failureFacade = FailureFacade(journalist),
            workingDirectory = directory,
            repositoryName = "releases",
            settings = FileSystemStorageProviderSettings(),
        )

    private val release = "org/example/app/1.0/app-1.0.jar"
    private val metadata = "org/example/app/maven-metadata.xml"
    private val content = "0123456789abcdefghij".toByteArray()

    /** Runs the scenario and returns what a client would observe, one line per observation. */
    private fun observe(storage: StorageProvider): List<String> {
        val lines = mutableListOf<String>()
        val releaseLocation = Location.of(release)
        val metadataLocation = Location.of(metadata)

        lines += "put release: ${storage.putFile(releaseLocation, content.inputStream()).isOk}"
        lines += "read release: ${storage.getFile(releaseLocation).get().use { it.readBytes().toList() }}"
        lines += "read release again: ${storage.getFile(releaseLocation).get().use { it.readBytes().toList() }}"
        lines += "range release: ${storage.getFile(releaseLocation, 4, 6).get().use { it.readBytes().toList() }}"
        lines += "size release: ${storage.getFileSize(releaseLocation).get()}"
        lines += "exists release: ${storage.exists(releaseLocation)}"
        lines += "put metadata: ${storage.putFile(metadataLocation, "<metadata/>".toByteArray().inputStream()).isOk}"
        lines += "read metadata: ${storage.getFile(metadataLocation).get().use { it.readBytes().toList() }}"
        lines += "remove release: ${storage.removeFile(releaseLocation).isOk}"
        lines += "read removed release: ${storage.getFile(releaseLocation).isOk}"
        lines += "exists removed release: ${storage.exists(releaseLocation)}"
        return lines
    }

    @Test
    fun `the cache observes the same results as the plain file system provider`() {
        val plain = observe(fileSystem(root.resolve("plain")))

        val cached = observe(
            CacheStorageProvider(
                delegate = fileSystem(root.resolve("delegate")),
                directory = root.resolve("cache"),
                repositoryName = "releases",
            )
        )

        assertThat(cached)
            .describedAs("the cache must not change what a client observes")
            .containsExactlyElementsOf(plain)
    }

}
