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
import org.junit.jupiter.api.Test

/**
 * The column limits of section 2.8. A value over its limit skips the file and counts it as
 * too long; it is never truncated, because a truncated coordinate would index the file under a
 * wrong name. Pure: no storage, no clock.
 */
internal class ArtifactPathLimitsTest {

    @Test
    fun `should accept a repository name of exactly the column limit`() {
        // given: a repository name of 128 characters and a plain release path
        val repository = "r".repeat(SearchColumn.REPOSITORY.limit)

        // when: the path is parsed
        val result = ArtifactPathParser.parse(repository, "com/example/app/1.0/app-1.0.jar")

        // then: the file is a row
        assertThat(result).describedAs("result").isInstanceOf(ArtifactPathResult.Row::class.java)
    }

    @Test
    fun `should skip every file of a repository name over the column limit`() {
        // given: a repository name of 129 characters
        val repository = "r".repeat(SearchColumn.REPOSITORY.limit + 1)

        // when: the path is parsed
        val result = ArtifactPathParser.parse(repository, "com/example/app/1.0/app-1.0.jar")

        // then: the file is skipped as too long, naming the repository column
        assertThat(result).describedAs("result").isEqualTo(ArtifactPathResult.TooLong(SearchColumn.REPOSITORY))
    }

    @Test
    fun `should skip a path over the column limit`() {
        // given: a path of about 1000 characters whose coordinates each fit their own limit
        val artifact = "a".repeat(200)
        val version = "1".repeat(200)
        val path = "${"g".repeat(200)}/$artifact/$version/$artifact-$version.jar"

        // when: the path is parsed
        val result = ArtifactPathParser.parse("releases", path)

        // then: the path column is the one that is too long
        assertThat(result).describedAs("result").isEqualTo(ArtifactPathResult.TooLong(SearchColumn.PATH))
    }

    @Test
    fun `should skip a file whose group id exceeds the column limit`() {
        // given: a group segment of 256 characters
        val group = "g".repeat(SearchColumn.GROUP_ID.limit + 1)

        // when: the path is parsed
        val result = ArtifactPathParser.parse("releases", "$group/app/1.0/app-1.0.jar")

        // then: the group id column is too long
        assertThat(result).describedAs("result").isEqualTo(ArtifactPathResult.TooLong(SearchColumn.GROUP_ID))
    }

    @Test
    fun `should skip a file whose classifier exceeds the column limit`() {
        // given: a classifier of 256 characters
        val classifier = "c".repeat(SearchColumn.CLASSIFIER.limit + 1)

        // when: the path is parsed
        val result = ArtifactPathParser.parse("releases", "com/example/app/1.0/app-1.0-$classifier.jar")

        // then: the classifier column is too long
        assertThat(result).describedAs("result").isEqualTo(ArtifactPathResult.TooLong(SearchColumn.CLASSIFIER))
    }

    @Test
    fun `should skip a file whose extension exceeds the column limit`() {
        // given: an extension of 65 characters
        val extension = "x".repeat(SearchColumn.EXTENSION.limit + 1)

        // when: the path is parsed
        val result = ArtifactPathParser.parse("releases", "com/example/app/1.0/app-1.0.$extension")

        // then: the extension column is too long
        assertThat(result).describedAs("result").isEqualTo(ArtifactPathResult.TooLong(SearchColumn.EXTENSION))
    }
}
