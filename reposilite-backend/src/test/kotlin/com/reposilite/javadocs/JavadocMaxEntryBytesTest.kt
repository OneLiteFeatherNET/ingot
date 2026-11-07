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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import panda.std.ResultAssertions.assertError
import panda.std.ResultAssertions.assertOk
import panda.std.reactive.Reference
import kotlin.io.path.exists
import kotlin.io.path.fileSize

internal class JavadocMaxEntryBytesTest : JavadocSpecification() {

    private val oldDefaultBytes = 2L * 1024 * 1024
    private val entryBetweenOldAndNewDefault = 3 * 1024 * 1024

    @Test
    fun `should serve an entry between the old and the new default limit`() {
        // given: a service built with the production defaults and a jar with an entry above the old 2 MiB limit
        val service = JavadocContainerService(
            failureFacade = javadocFailureFacade,
            mavenFacade = mavenFacade,
            javadocFolder = workingDirectory.toPath().resolve("javadocs"),
            suffixes = Reference.reference(listOf("-javadoc.jar")),
        )
        val (jar, unpack) = stageJavadocJar("default-limit")
        writeJar(jar, mapOf(
            "index.html" to "<html></html>".toByteArray(),
            "Big.html" to ByteArray(entryBetweenOldAndNewDefault)
        ))

        // when: the jar is unpacked
        val result = service.unpackJavadocJar(jar, unpack)

        // then: the entry is served in full
        assertOk(result)
        assertThat(unpack.resolve("Big.html").exists())
            .describedAs("entry of 3 MiB must be extracted with the default limit")
            .isTrue
        assertThat(unpack.resolve("Big.html").fileSize())
            .describedAs("extracted entry must keep its full size")
            .isEqualTo(entryBetweenOldAndNewDefault.toLong())
    }

    @Test
    fun `should use the default limit when no javadoc property is set`() {
        // when: the limit is resolved without any property
        val limit = javadocMaxEntryBytes { null }

        // then: the limit is the new default, above the old 2 MiB
        assertThat(limit)
            .describedAs("default javadoc entry limit without properties")
            .isEqualTo(DEFAULT_JAVADOC_MAX_ENTRY_BYTES)
        assertThat(limit)
            .describedAs("new default must exceed the old 2 MiB limit")
            .isGreaterThan(oldDefaultBytes)
    }

    @Test
    fun `should honour the ingot override`() {
        // given: an operator sets the Ingot property to 1 KiB
        val limit = javadocMaxEntryBytes(mapOf("ingot.javadocs.max-entry-bytes" to "1024")::get)

        // then: the resolved limit is the override
        assertThat(limit)
            .describedAs("ingot.javadocs.max-entry-bytes must replace the default")
            .isEqualTo(1024L)
    }

    @Test
    fun `should reject an entry above the overridden limit`() {
        // given: a service with the 1 KiB override and a jar with a 2 KiB entry
        val service = JavadocContainerService(
            failureFacade = javadocFailureFacade,
            mavenFacade = mavenFacade,
            javadocFolder = workingDirectory.toPath().resolve("javadocs"),
            suffixes = Reference.reference(listOf("-javadoc.jar")),
            maxEntryBytes = javadocMaxEntryBytes(mapOf("ingot.javadocs.max-entry-bytes" to "1024")::get),
        )
        val (jar, unpack) = stageJavadocJar("override-limit")
        writeJar(jar, mapOf(
            "index.html" to "<html></html>".toByteArray(),
            "big" to ByteArray(2048)
        ))

        // when: the jar is unpacked
        val result = service.unpackJavadocJar(jar, unpack)

        // then: the entry exceeds the override and is rejected
        assertError(result)
    }

    @Test
    fun `should prefer the ingot property over the legacy reposilite property`() {
        // given: both spellings are set
        val properties = mapOf(
            "ingot.javadocs.max-entry-bytes" to "4096",
            "reposilite.javadocs.max-entry-bytes" to "8192",
        )

        // then: the Ingot value wins
        assertThat(javadocMaxEntryBytes(properties::get))
            .describedAs("ingot.javadocs.max-entry-bytes must win over the legacy property")
            .isEqualTo(4096L)
    }

    @Test
    fun `should fall back to the legacy reposilite property`() {
        // given: only the legacy spelling is set
        val properties = mapOf("reposilite.javadocs.max-entry-bytes" to "8192")

        // then: the legacy value is used
        assertThat(javadocMaxEntryBytes(properties::get))
            .describedAs("reposilite.javadocs.max-entry-bytes must still be honoured")
            .isEqualTo(8192L)
    }
}
