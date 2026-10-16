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

package net.onelitefeather.ingot.search

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * The path rules of section 2.2 of the artifact search spec, one row per example. Each row is a
 * path inside a repository and the outcome the parser must produce for it. Pure: no storage, no
 * database, no clock.
 */
internal class ArtifactPathParserTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("pathRules")
    fun `should classify a repository path by the section 2_2 rules`(path: String, expected: ArtifactPathResult) {
        // when: the path is parsed inside its repository
        val result = ArtifactPathParser.parse(REPOSITORY, path)

        // then: the coordinates or the classification match the table
        assertThat(result).describedAs("outcome for $path").isEqualTo(expected)
    }

    companion object {

        private const val REPOSITORY = "releases"
        private const val COMMONS = "org/apache/commons/commons-lang3/3.14.0/"
        private const val APP = "com/example/app/"

        @JvmStatic
        fun pathRules(): Stream<Arguments> = Stream.of(
            Arguments.of(
                COMMONS + "commons-lang3-3.14.0.jar",
                row(COMMONS + "commons-lang3-3.14.0.jar", "org.apache.commons", "commons-lang3", "3.14.0", null, "jar"),
            ),
            Arguments.of(
                COMMONS + "commons-lang3-3.14.0-sources.jar",
                row(COMMONS + "commons-lang3-3.14.0-sources.jar", "org.apache.commons", "commons-lang3", "3.14.0", "sources", "jar"),
            ),
            Arguments.of(
                COMMONS + "commons-lang3-3.14.0-sources.jar.asc",
                row(COMMONS + "commons-lang3-3.14.0-sources.jar.asc", "org.apache.commons", "commons-lang3", "3.14.0", "sources", "jar.asc"),
            ),
            Arguments.of(
                COMMONS + "commons-lang3-3.14.0.pom.asc",
                row(COMMONS + "commons-lang3-3.14.0.pom.asc", "org.apache.commons", "commons-lang3", "3.14.0", null, "pom.asc"),
            ),
            Arguments.of(
                APP + "1.0/app-1.0-bin.tar.gz",
                row(APP + "1.0/app-1.0-bin.tar.gz", "com.example", "app", "1.0", "bin", "tar.gz"),
            ),
            Arguments.of(
                APP + "1.0/app-1.0.tar.gz",
                row(APP + "1.0/app-1.0.tar.gz", "com.example", "app", "1.0", null, "tar.gz"),
            ),
            Arguments.of(
                APP + "1.0/app-1.0-jdk1.8.jar",
                row(APP + "1.0/app-1.0-jdk1.8.jar", "com.example", "app", "1.0", "jdk1.8", "jar"),
            ),
            Arguments.of(
                APP + "1.0-SNAPSHOT/app-1.0-20260101.120000-3.jar",
                row(APP + "1.0-SNAPSHOT/app-1.0-20260101.120000-3.jar", "com.example", "app", "1.0-SNAPSHOT", null, "jar", snapshot = true),
            ),
            Arguments.of(
                APP + "1.0-SNAPSHOT/app-1.0-20260101.120000-3-sources.jar",
                row(APP + "1.0-SNAPSHOT/app-1.0-20260101.120000-3-sources.jar", "com.example", "app", "1.0-SNAPSHOT", "sources", "jar", snapshot = true),
            ),
            Arguments.of(
                APP + "1.0-SNAPSHOT/app-1.0-SNAPSHOT.jar",
                row(APP + "1.0-SNAPSHOT/app-1.0-SNAPSHOT.jar", "com.example", "app", "1.0-SNAPSHOT", null, "jar", snapshot = true),
            ),
            Arguments.of(
                "junit/junit/4.13.2/junit-4.13.2.jar",
                row("junit/junit/4.13.2/junit-4.13.2.jar", "junit", "junit", "4.13.2", null, "jar"),
            ),
            Arguments.of(
                APP + "1.0/app-1.0.jar.sha1",
                ArtifactPathResult.Ignored(IgnoreReason.METADATA_OR_CHECKSUM),
            ),
            Arguments.of(
                APP + "maven-metadata.xml",
                ArtifactPathResult.Ignored(IgnoreReason.METADATA_OR_CHECKSUM),
            ),
            Arguments.of(
                APP + "1.0/notes.txt",
                ArtifactPathResult.Unparsed(UnparsedReason.NAME_PREFIX),
            ),
        )

        private fun row(
            path: String,
            groupId: String,
            artifactId: String,
            version: String,
            classifier: String?,
            extension: String,
            snapshot: Boolean = false,
        ): ArtifactPathResult.Row = ArtifactPathResult.Row(
            ArtifactFileRecord(
                repository = REPOSITORY,
                path = path,
                groupId = groupId,
                artifactId = artifactId,
                version = version,
                classifier = classifier,
                extension = extension,
                snapshot = snapshot,
            ),
        )
    }
}
