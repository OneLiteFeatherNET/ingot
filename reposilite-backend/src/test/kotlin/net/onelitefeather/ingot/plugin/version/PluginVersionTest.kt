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

package net.onelitefeather.ingot.plugin.version

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource

/**
 * The version grammar and order of the design (D3). The parser is a pure object, so no test needs a class loader,
 * a file or the clock.
 */
internal class PluginVersionTest {

    @ParameterizedTest(name = "{0} is higher than {1}")
    @CsvSource(
        "1.2.10, 1.2.9",
        "2.0.0, 1.9.9",
        "1.0.1, 1.0.0",
        "1.0.0, 1.0.0-SNAPSHOT",
    )
    fun `should order a higher version above a lower one`(higher: String, lower: String) {
        // given: two valid versions
        val upper = PluginVersion.parse(higher)
        val lowerVersion = PluginVersion.parse(lower)

        // when: they are compared
        val result = PluginVersion.compareOrLowest(upper, lowerVersion)

        // then: the first one is higher
        assertThat(result)
            .describedAs("$higher compared with $lower")
            .isPositive()
    }

    @Test
    fun `should count a missing number as zero`() {
        // given: a version with two numbers and one with three
        val short = PluginVersion.parse("1.2")
        val full = PluginVersion.parse("1.2.0")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(short, full)

        // then: they are equal
        assertThat(result)
            .describedAs("1.2 compared with 1.2.0")
            .isZero()
    }

    @Test
    fun `should order a pre-release below the release with the same numbers`() {
        // given: a snapshot and its release
        val snapshot = PluginVersion.parse("1.0.0-SNAPSHOT")
        val release = PluginVersion.parse("1.0.0")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(snapshot, release)

        // then: the snapshot is lower
        assertThat(result)
            .describedAs("1.0.0-SNAPSHOT compared with 1.0.0")
            .isNegative()
    }

    @Test
    fun `should treat two pre-releases of the same numbers as equal`() {
        // given: two different pre-release labels on the same numbers
        val alpha = PluginVersion.parse("1.0.0-alpha")
        val snapshot = PluginVersion.parse("1.0.0-SNAPSHOT")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(alpha, snapshot)

        // then: neither is strictly higher
        assertThat(result)
            .describedAs("1.0.0-alpha compared with 1.0.0-SNAPSHOT")
            .isZero()
    }

    @Test
    fun `should ignore the build suffix`() {
        // given: a version with a build suffix and the plain version
        val withBuild = PluginVersion.parse("1.0.0+build.7")
        val plain = PluginVersion.parse("1.0.0")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(withBuild, plain)

        // then: they are equal
        assertThat(result)
            .describedAs("1.0.0+build.7 compared with 1.0.0")
            .isZero()
    }

    @ParameterizedTest(name = "\"{0}\" is not a valid version")
    @ValueSource(strings = ["1.2.3.4", "latest", "v1.0.0", "1.x", "1.0.0-"])
    fun `should reject a string that is not a version`(raw: String) {
        // when: the string is parsed
        val version = PluginVersion.parse(raw)

        // then: no version is produced
        assertThat(version)
            .describedAs("parse of \"$raw\"")
            .isNull()
    }

    @ParameterizedTest(name = "a blank value \"{0}\" is not a valid version")
    @NullAndEmptySource
    @ValueSource(strings = [" ", "\t"])
    fun `should reject a missing or blank version`(raw: String?) {
        // when: the value is parsed
        val version = PluginVersion.parse(raw)

        // then: no version is produced
        assertThat(version)
            .describedAs("parse of ${raw?.let { "\"$it\"" } ?: "null"}")
            .isNull()
    }

    @Test
    fun `should count a missing version as lower than a valid one`() {
        // given: no version and a valid version
        val valid = PluginVersion.parse("0.0.1")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(null, valid)

        // then: the missing version is lower
        assertThat(result)
            .describedAs("missing version compared with 0.0.1")
            .isNegative()
    }

    @Test
    fun `should count an invalid version as lower than a valid one`() {
        // given: an invalid version, parsed as missing, and a valid version
        val invalid = PluginVersion.parse("latest")
        val valid = PluginVersion.parse("0.0.1")

        // when: they are compared
        val result = PluginVersion.compareOrLowest(invalid, valid)

        // then: the invalid version is lower
        assertThat(result)
            .describedAs("invalid version compared with 0.0.1")
            .isNegative()
    }

    @Test
    fun `should treat two missing versions as equal`() {
        // when: two missing versions are compared
        val result = PluginVersion.compareOrLowest(null, null)

        // then: they are equal
        assertThat(result)
            .describedAs("missing compared with missing")
            .isZero()
    }

}
