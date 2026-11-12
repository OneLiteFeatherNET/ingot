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

import com.reposilite.storage.api.toLocation
import net.onelitefeather.ingot.format.ExperimentalIngotFormatApi
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The Maven path rules against one table of paths. Every expected value is the answer of the code the rule replaces on main:
 * the `maven-metadata.xml` checks of the repository, the metadata and checksum check of the mirror cache, the resolution
 * metadata of the resolution provider, the ignored extensions of the download counter and the artifact index path. The table
 * is written out by hand, so a change in any answer shows up as a failing row.
 */
@OptIn(ExperimentalIngotFormatApi::class)
internal class MavenPathRulesCharacterisationTest {

    private data class Row(
        val path: String,
        /** Contains `maven-metadata.xml` in its name: the deploy and cache rules and the mirror metadata policy. */
        val generatedMetadata: Boolean,
        /** Metadata or checksum by name: the mirror checksum policy and the provenance of a directory. */
        val metadataOrChecksum: Boolean,
        /** Metadata that the resolution cache may remember: `.pom`, `.module`, `maven-metadata.xml`, `ivy.xml` and `ivy-*.xml`. */
        val resolutionMetadata: Boolean,
        /** Not in the ignored extensions of the download counter. */
        val countsAsDownload: Boolean,
        /** The path the artifact index is told about, or null for metadata. */
        val indexPath: String?,
    )

    private val table = listOf(
        Row("com/example/a/1.0/a-1.0.jar", false, false, false, true, "com/example/a/1.0/a-1.0.jar"),
        Row("com/example/a/1.0/a-1.0.pom", false, false, true, false, "com/example/a/1.0/a-1.0.pom"),
        Row("com/example/a/1.0/a-1.0.module", false, false, true, false, "com/example/a/1.0/a-1.0.module"),
        Row("com/example/a/1.0/a-1.0.jar.sha1", false, true, false, false, "com/example/a/1.0/a-1.0.jar"),
        Row("com/example/a/1.0/a-1.0.pom.asc", false, false, false, false, "com/example/a/1.0/a-1.0.pom.asc"),
        Row("com/example/a/maven-metadata.xml", true, true, true, false, null),
        Row("com/example/a/maven-metadata.xml.sha1", true, true, false, false, null),
        Row("com/example/a/1.0-SNAPSHOT/maven-metadata.xml", true, true, true, false, null),
        Row("com/example/a/1.0-SNAPSHOT/a-1.0-20260101.120000-1.jar", false, false, false, true, "com/example/a/1.0-SNAPSHOT/a-1.0-20260101.120000-1.jar"),
        Row("com/example/a/1.0/a-1.0-sources.jar", false, false, false, false, "com/example/a/1.0/a-1.0-sources.jar"),
        Row("com/example/a/1.0/a-1.0-javadoc.jar", false, false, false, false, "com/example/a/1.0/a-1.0-javadoc.jar"),
        Row("com/example/a/1.0/ivy-1.0.xml", false, false, true, false, "com/example/a/1.0/ivy-1.0.xml"),
        Row("com/example/a/1.0/ivy.xml", false, false, true, false, "com/example/a/1.0/ivy.xml"),
        Row("com/example/a/1.0/a-1.0.xml", false, false, false, false, "com/example/a/1.0/a-1.0.xml"),
        Row("com/example/a/1.0/README", false, false, false, true, "com/example/a/1.0/README"),
        Row("com/example/a/1.0/native-binary", false, false, false, true, "com/example/a/1.0/native-binary"),
        Row("com/example/a/1.0/a-1.0.jar.md5", false, true, false, false, "com/example/a/1.0/a-1.0.jar"),
        Row("com/example/a/1.0/a-1.0.jar.sha512", false, true, false, false, "com/example/a/1.0/a-1.0.jar"),
        Row("com/example/a/1.0/a-1.0.jar.sigstore.json", false, false, false, true, "com/example/a/1.0/a-1.0.jar.sigstore.json"),
        Row("com/example/a/1.0/a-1.0.pom.sha256", false, true, false, false, "com/example/a/1.0/a-1.0.pom"),
    )

    private val rules = Maven2Format().pathRules

    @Test
    fun `generated metadata is recognised by its name as the deploy and cache rules did`() {
        table.forEach { row ->
            assertThat(rules.isGeneratedMetadata(row.path.toLocation()))
                .describedAs("isGeneratedMetadata of ${row.path}")
                .isEqualTo(row.generatedMetadata)
        }
    }

    @Test
    fun `metadata and checksum files are recognised by name as the mirror checksum policy did`() {
        table.forEach { row ->
            assertThat(rules.isMetadataOrChecksum(row.path.toLocation()))
                .describedAs("isMetadataOrChecksum of ${row.path}")
                .isEqualTo(row.metadataOrChecksum)
        }
    }

    @Test
    fun `resolution metadata is recognised by extension and name as the resolution provider did`() {
        table.forEach { row ->
            assertThat(rules.isResolutionMetadata(row.path.toLocation()))
                .describedAs("isResolutionMetadata of ${row.path}")
                .isEqualTo(row.resolutionMetadata)
        }
    }

    @Test
    fun `a read counts as a download unless the file is one of the ignored extensions`() {
        table.forEach { row ->
            assertThat(rules.countsAsDownload(row.path.toLocation()))
                .describedAs("countsAsDownload of ${row.path}")
                .isEqualTo(row.countsAsDownload)
        }
    }

    @Test
    fun `the index path of a checksum is its base file and metadata has none`() {
        table.forEach { row ->
            assertThat(rules.indexPath(row.path.toLocation()))
                .describedAs("indexPath of ${row.path}")
                .isEqualTo(row.indexPath)
        }
    }

}
