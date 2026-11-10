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

package net.onelitefeather.ingot.web

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

internal class RequestLimitSettingsTest {

    @Test
    fun `should not limit requests when nothing is set`() {
        // when: the limit is read from an empty environment and no properties
        val limit = RequestLimitSettings.read(emptyMap(), emptyMap())

        // then: there is no limit, which keeps the behaviour of earlier versions
        assertThat(limit).`as`("concurrent request limit").isNull()
    }

    @Test
    fun `should read a positive whole number from the ingot name`() {
        // when: the limit is set with the ingot name
        val limit = RequestLimitSettings.read(mapOf("INGOT_HTTP_MAXCONCURRENT" to "5"), emptyMap())

        // then: the limit is five requests
        assertThat(limit).`as`("concurrent request limit").isEqualTo(5)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "-1", "many", "2.5", "+3", "99999999999"])
    fun `should refuse a value that is not a positive whole number and name the variable`(value: String) {
        // when: the limit is set to a value that is not a positive whole number
        // then: the read fails and the message names the variable
        assertThatThrownBy { RequestLimitSettings.read(mapOf("INGOT_HTTP_MAXCONCURRENT" to value), emptyMap()) }
            .`as`("rejection of '$value'")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_HTTP_MAXCONCURRENT")
    }

    @Test
    fun `should fall back to the reposilite name when only the legacy variable is set`() {
        // when: only the legacy name is set
        val limit = RequestLimitSettings.read(mapOf("REPOSILITE_HTTP_MAXCONCURRENT" to "4"), emptyMap())

        // then: the legacy value is the limit
        assertThat(limit).`as`("concurrent request limit from the fallback").isEqualTo(4)
    }

    @Test
    fun `should prefer the ingot name over the reposilite name`() {
        // when: both names are set with different values
        val limit = RequestLimitSettings.read(
            mapOf("INGOT_HTTP_MAXCONCURRENT" to "8", "REPOSILITE_HTTP_MAXCONCURRENT" to "4"),
            emptyMap(),
        )

        // then: the ingot value wins
        assertThat(limit).`as`("concurrent request limit").isEqualTo(8)
    }

    @Test
    fun `should prefer the system property over both environment names`() {
        // when: the property and both environment names are set
        val limit = RequestLimitSettings.read(
            mapOf("INGOT_HTTP_MAXCONCURRENT" to "8", "REPOSILITE_HTTP_MAXCONCURRENT" to "4"),
            mapOf("ingot.http.maxConcurrent" to "2"),
        )

        // then: the property wins
        assertThat(limit).`as`("concurrent request limit").isEqualTo(2)
    }

    @Test
    fun `should name the property when the system property is invalid`() {
        // when: the property is set to zero
        // then: the read fails and the message names the property
        assertThatThrownBy { RequestLimitSettings.read(emptyMap(), mapOf("ingot.http.maxConcurrent" to "0")) }
            .`as`("rejection of the property")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ingot.http.maxConcurrent")
    }

    @Test
    fun `should treat a blank ingot value as unset and use the reposilite name`() {
        // when: the ingot name is blank and the legacy name is set
        val limit = RequestLimitSettings.read(
            mapOf("INGOT_HTTP_MAXCONCURRENT" to "   ", "REPOSILITE_HTTP_MAXCONCURRENT" to "3"),
            emptyMap(),
        )

        // then: the blank value does not count, so the legacy value is the limit
        assertThat(limit).`as`("concurrent request limit").isEqualTo(3)
    }

}
