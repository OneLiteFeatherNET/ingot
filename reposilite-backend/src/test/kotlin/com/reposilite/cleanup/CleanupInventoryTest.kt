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

package com.reposilite.cleanup

import com.reposilite.storage.StoredFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

internal class CleanupInventoryTest {

    private val now: Instant = Instant.parse("2026-10-15T12:00:00Z")

    private fun file(path: String, size: Long = 1, ageDays: Int? = 1) =
        StoredFile(path, size, ageDays?.let { now.minus(Duration.ofDays(it.toLong())) })

    @Test
    fun `should treat a directory with a pom file as a version of its parent artifact`() {
        // given: the files of one version of one artifact
        val files = listOf(file("net/example/app/1.0/app-1.0.jar"), file("net/example/app/1.0/app-1.0.pom"))

        // when: the inventory is built
        val versions = CleanupInventory.versionsOf("releases", files, cached = false)

        // then: the directory 1.0 is one version of net/example/app
        val version = versions.single()
        assertEquals("releases", version.repository)
        assertEquals("net/example/app", version.artifact)
        assertEquals("1.0", version.version)
        assertEquals("net/example/app/1.0", version.path)
    }

    @Test
    fun `should ignore directories that contain no pom file`() {
        // given: a directory with a jar but no pom
        val files = listOf(file("net/example/app/1.0/app-1.0.jar"))

        // when: the inventory is built
        val versions = CleanupInventory.versionsOf("releases", files, cached = false)

        // then: the directory is not a version
        assertTrue(versions.isEmpty())
    }

    @Test
    fun `should sum the sizes of every file of a version`() {
        // given: a jar, a pom and a checksum of one version
        val files = listOf(
            file("net/example/app/1.0/app-1.0.jar", size = 5),
            file("net/example/app/1.0/app-1.0.pom", size = 3),
            file("net/example/app/1.0/app-1.0.pom.sha1", size = 1),
        )

        // when: the inventory is built
        val version = CleanupInventory.versionsOf("releases", files, cached = false).single()

        // then: the version weighs the sum of its files
        assertEquals(9L, version.sizeBytes)
    }

    @Test
    fun `should count files in nested folders of a version towards its size`() {
        // given: a pom and a nested documentation file of one version
        val files = listOf(
            file("net/example/app/1.0/app-1.0.pom", size = 3),
            file("net/example/app/1.0/docs/readme.txt", size = 7),
        )

        // when: the inventory is built
        val version = CleanupInventory.versionsOf("releases", files, cached = false).single()

        // then: the nested file is part of the version
        assertEquals(10L, version.sizeBytes)
    }

    @Test
    fun `should use the newest modification time of the files of a version`() {
        // given: a pom modified 10 days ago and a jar modified 2 days ago
        val files = listOf(
            file("net/example/app/1.0/app-1.0.pom", ageDays = 10),
            file("net/example/app/1.0/app-1.0.jar", ageDays = 2),
        )

        // when: the inventory is built
        val version = CleanupInventory.versionsOf("releases", files, cached = false).single()

        // then: the version is as recent as its newest file
        assertEquals(now.minus(Duration.ofDays(2)), version.lastModified)
    }

    @Test
    fun `should not treat the metadata file of an artifact as a version`() {
        // given: an artifact directory with its maven-metadata.xml next to one version
        val files = listOf(
            file("net/example/app/maven-metadata.xml"),
            file("net/example/app/1.0/app-1.0.pom"),
        )

        // when: the inventory is built
        val versions = CleanupInventory.versionsOf("releases", files, cached = false)

        // then: only the version directory is listed
        assertEquals(listOf("net/example/app/1.0"), versions.map { it.path })
    }

    @Test
    fun `should skip a version without any modification time`() {
        // given: a version whose files report no modification time
        val files = listOf(file("net/example/app/1.0/app-1.0.pom", ageDays = null))

        // when: the inventory is built
        val versions = CleanupInventory.versionsOf("releases", files, cached = false)

        // then: the version is not inventoried, so no policy can judge its age
        assertTrue(versions.isEmpty())
    }

    @Test
    fun `should mark every version as cached when the repository stores mirror copies`() {
        // given: one version in a mirror repository
        val files = listOf(file("org/mirror/lib/2.0/lib-2.0.pom"))

        // when: the inventory is built for a repository that stores mirrored artifacts
        val version = CleanupInventory.versionsOf("central", files, cached = true).single()

        // then: the version is a mirror cache entry
        assertTrue(version.cached)
    }

}
