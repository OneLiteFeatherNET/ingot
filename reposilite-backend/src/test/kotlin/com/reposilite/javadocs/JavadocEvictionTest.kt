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

package com.reposilite.javadocs

import com.reposilite.javadocs.specification.JavadocSpecification
import com.reposilite.journalist.Journalist
import com.reposilite.journalist.Logger
import com.reposilite.journalist.backend.InMemoryLogger
import com.reposilite.maven.application.RepositorySettings
import com.reposilite.storage.api.toLocation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

internal class JavadocEvictionTest : JavadocSpecification() {

    override fun repositories(): List<RepositorySettings> =
        listOf(RepositorySettings(id = "hosted"))

    private fun javadocFolder(): Path =
        workingDirectory.toPath().resolve("javadocs")

    private fun newFacade(): JavadocFacade =
        JavadocFacade(
            journalist = object : Journalist {
                override fun getLogger(): Logger = InMemoryLogger()
            },
            javadocFolder = javadocFolder(),
            mavenFacade = mavenFacade,
            javadocContainerService = javadocContainerService,
        )

    /** Creates an unpacked javadoc copy of [artifact] in the cache of [repository], as a previous request would have. */
    private fun unpackedCopy(repository: String, artifact: String): Path {
        val cache = javadocFolder().resolve(repository).resolve(artifact).resolve(".cache")
        cache.resolve("unpack").createDirectories()
        cache.resolve("index.html").writeText("<html>$artifact</html>")
        return cache
    }

    @Test
    fun `evicting an artifact removes its cache tree and keeps the cache of a sibling artifact`() {
        // given: unpacked copies of two artifacts of the same group
        val evicted = unpackedCopy("hosted", "com/example/lib")
        val sibling = unpackedCopy("hosted", "com/example/other")

        // when: the artifact root of the first one is evicted
        newFacade().evictArtifact("hosted", "com/example/lib".toLocation())

        // then: its cache is gone and the sibling keeps its copy
        assertThat(evicted.exists()).describedAs("cache of com/example/lib removed").isFalse()
        assertThat(sibling.resolve("index.html").exists()).describedAs("cache of com/example/other kept").isTrue()
    }

    @Test
    fun `a received path that climbs out of the repository root is refused and deletes nothing`() {
        // given: a cache of the repository and a directory next to it that a traversal would reach
        val cache = unpackedCopy("hosted", "com/example/lib")
        val outside = javadocFolder().resolve("other").resolve("com/example/lib/.cache").also { it.createDirectories() }
        outside.resolve("index.html").writeText("<html>outside</html>")

        // when: an event names a path that leaves the repository root
        val result = newFacade().evictReceived("hosted", "../../other/com/example/lib")

        // then: the event is refused and nothing is removed
        assertError(result)
        assertThat(outside.resolve("index.html").exists()).describedAs("directory outside the root kept").isTrue()
        assertThat(cache.resolve("index.html").exists()).describedAs("cache inside the root kept").isTrue()
    }

    @Test
    fun `a received absolute path is refused and deletes nothing`() {
        // given: a cache of the repository
        val cache = unpackedCopy("hosted", "com/example/lib")

        // when: an event names an absolute path
        val result = newFacade().evictReceived("hosted", javadocFolder().toAbsolutePath().toString())

        // then: the event is refused and the cache is kept
        assertError(result)
        assertThat(cache.resolve("index.html").exists()).describedAs("cache kept after a refused absolute path").isTrue()
    }

    @Test
    fun `a received event for an unknown repository deletes nothing`() {
        // given: a cache directory under a repository name the instance does not have
        val cache = unpackedCopy("unknown", "com/example/lib")

        // when: an event names that repository
        val result = newFacade().evictReceived("unknown", "com/example/lib")

        // then: the event is refused and the directory is kept
        assertError(result)
        assertThat(cache.resolve("index.html").exists()).describedAs("cache of an unknown repository kept").isTrue()
    }

    @Test
    fun `evicting an artifact without a cache is a no-op`() {
        // given: no cache for the artifact

        // when: the eviction of the artifact is received
        val result = newFacade().evictReceived("hosted", "com/example/absent")

        // then: the event is accepted and nothing fails
        assertOk(result)
    }

    @Test
    fun `a jar without a javadoc suffix has no artifact root to evict`() {
        // given: the javadoc suffixes of the server and a plain binary jar of an artifact

        // when: the artifact root of the jar is resolved for an eviction
        val root = javadocEvictionRoot("com/example/lib/1.0/lib-1.0.jar".toLocation(), listOf("-javadoc.jar"))

        // then: nothing is evicted and nothing is published
        assertThat(root).describedAs("no eviction root for a non javadoc jar").isNull()
    }

    @Test
    fun `a javadoc jar names the artifact root two levels above the jar`() {
        // given: a javadoc jar of version 1.0 of an artifact

        // when: the artifact root of the jar is resolved for an eviction
        val root = javadocEvictionRoot("com/example/lib/1.0/lib-1.0-javadoc.jar".toLocation(), listOf("-javadoc.jar"))

        // then: the root is the artifact directory that holds every version
        assertThat(root.toString()).describedAs("artifact root").isEqualTo("com/example/lib")
    }
}
