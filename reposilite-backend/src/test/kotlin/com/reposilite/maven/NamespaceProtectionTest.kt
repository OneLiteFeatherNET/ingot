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
import org.junit.jupiter.api.Test

internal class NamespaceProtectionTest {

    @Test
    fun `should cover a coordinate inside the prefix`() {
        // given: a protected prefix and a jar of a group below it
        val gav = "net/onelitefeather/ingot/1.0/ingot-1.0.jar".toLocation()

        // when: the coordinate is matched against the prefix
        val covered = NamespaceProtection.covers("net.onelitefeather", gav)

        // then: the coordinate is protected
        assertThat(covered).describedAs("coverage of a jar below the prefix").isTrue()
    }

    @Test
    fun `should cover metadata directly below the prefix`() {
        // given: the metadata file of the group that the prefix names
        val gav = "net/onelitefeather/maven-metadata.xml".toLocation()

        // when: the coordinate is matched against the prefix
        val covered = NamespaceProtection.covers("net.onelitefeather", gav)

        // then: the metadata file is protected
        assertThat(covered).describedAs("coverage of the metadata of the prefix").isTrue()
    }

    @Test
    fun `should not cover a look-alike group that only starts with the same characters`() {
        // given: a group whose id starts with the prefix but is a different segment
        val gav = "net/onelitefeatherx/lib/1.0/lib-1.0.jar".toLocation()

        // when: the coordinate is matched against the prefix
        val covered = NamespaceProtection.covers("net.onelitefeather", gav)

        // then: the coordinate is not protected
        assertThat(covered).describedAs("coverage of net.onelitefeatherx by net.onelitefeather").isFalse()
    }

    @Test
    fun `should not cover a location shorter than the prefix`() {
        // given: the directory above the prefix
        val gav = "net".toLocation()

        // when: the location is matched against the prefix
        val covered = NamespaceProtection.covers("net.onelitefeather", gav)

        // then: an ancestor directory is not protected
        assertThat(covered).describedAs("coverage of an ancestor of the prefix").isFalse()
    }

    @Test
    fun `should match the prefix case sensitively`() {
        // given: the same group written with capital letters
        val gav = "net/OneLiteFeather/ingot/1.0/ingot-1.0.jar".toLocation()

        // when: the coordinate is matched against the lower case prefix
        val covered = NamespaceProtection.covers("net.onelitefeather", gav)

        // then: the coordinate is not protected, as the Maven layout is case sensitive
        assertThat(covered).describedAs("coverage with a different letter case").isFalse()
    }

    @Test
    fun `should cover a coordinate when any of the prefixes covers it`() {
        // given: two prefixes, only the second one covers the coordinate
        val gav = "com/example/lib/1.0/lib-1.0.jar".toLocation()

        // when: the coordinate is matched against the list
        val covered = NamespaceProtection.isProtected(listOf("net.onelitefeather", "com.example"), gav)

        // then: the coordinate is protected
        assertThat(covered).describedAs("coverage by the second of two prefixes").isTrue()
    }

    @Test
    fun `should accept a dotted group id as a prefix`() {
        assertThat(NamespaceProtection.isValidPrefix("net.onelitefeather.ingot")).describedAs("a dotted group id").isTrue()
    }

    @Test
    fun `should refuse a blank prefix`() {
        assertThat(NamespaceProtection.isValidPrefix("   ")).describedAs("a blank prefix").isFalse()
    }

    @Test
    fun `should refuse a prefix that starts or ends with a dot`() {
        assertThat(NamespaceProtection.isValidPrefix(".net.onelitefeather")).describedAs("a leading dot").isFalse()
        assertThat(NamespaceProtection.isValidPrefix("net.onelitefeather.")).describedAs("a trailing dot").isFalse()
    }

    @Test
    fun `should refuse a prefix with a slash`() {
        assertThat(NamespaceProtection.isValidPrefix("net/onelitefeather")).describedAs("a prefix written as a path").isFalse()
    }

    @Test
    fun `should refuse a prefix with an empty segment`() {
        assertThat(NamespaceProtection.isValidPrefix("net..onelitefeather")).describedAs("an empty segment").isFalse()
    }

}
