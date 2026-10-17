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
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/** Which path the artifact index is told about for a write of a location (artifact search spec, 3.5). */
internal class ArtifactIndexPathTest {

    @ParameterizedTest(name = "{0} is indexed as {1}")
    @CsvSource(
        "com/example/app/1.0.0/app-1.0.0.jar, com/example/app/1.0.0/app-1.0.0.jar",
        "com/example/app/1.0.0/app-1.0.0.pom, com/example/app/1.0.0/app-1.0.0.pom",
        "com/example/app/1.0.0/app-1.0.0.jar.md5, com/example/app/1.0.0/app-1.0.0.jar",
        "com/example/app/1.0.0/app-1.0.0.jar.sha1, com/example/app/1.0.0/app-1.0.0.jar",
        "com/example/app/1.0.0/app-1.0.0.pom.sha512, com/example/app/1.0.0/app-1.0.0.pom",
        "com/example/app/1.0.0/maven-metadata.xml, ",
        "com/example/app/1.0.0/maven-metadata.xml.sha256, ",
    )
    fun `should map a location to the path of its artifact or to no path for metadata`(location: String, expected: String?) {
        // when: the location is mapped for the index
        val indexed = location.toLocation().artifactIndexPath()

        // then: the artifact path is returned, and metadata and its checksums yield no path
        assertThat(indexed).`as`("index path of $location").isEqualTo(expected)
    }

}
