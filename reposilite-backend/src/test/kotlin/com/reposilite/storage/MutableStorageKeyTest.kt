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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MutableStorageKeyTest {

    @Test
    fun `maven metadata file is mutable`() {
        assertThat(isMutableStorageKey("org/example/app/maven-metadata.xml"))
            .describedAs("maven-metadata.xml changes with every deployment, so it must never be cached")
            .isTrue()
    }

    @Test
    fun `checksum of maven metadata is mutable`() {
        assertThat(isMutableStorageKey("org/example/app/maven-metadata.xml.sha1"))
            .describedAs("the checksum of the metadata follows the metadata")
            .isTrue()
    }

    @Test
    fun `file inside a snapshot directory is mutable`() {
        assertThat(isMutableStorageKey("org/example/app/1.0-SNAPSHOT/app-1.0-20260101.jar"))
            .describedAs("snapshot directories are overwritten by every deployment")
            .isTrue()
    }

    @Test
    fun `release file is not mutable`() {
        assertThat(isMutableStorageKey("org/example/app/1.0/app-1.0.jar"))
            .describedAs("a release file is immutable and may be cached")
            .isFalse()
    }

}
