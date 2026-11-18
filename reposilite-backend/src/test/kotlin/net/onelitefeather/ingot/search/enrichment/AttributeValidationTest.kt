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

package net.onelitefeather.ingot.search.enrichment

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class AttributeValidationTest {

    private val enricherId = "gradle-portal"

    private fun refusalOf(values: Map<String, List<String>>): AttributeRefusal? = validateAttributes(enricherId, values)

    @Test
    fun `an allowed attribute set passes validation`() {
        val refusal = refusalOf(mapOf("gradle-portal.loader" to listOf("fabric"), "gradle-portal.tags" to listOf("a", "b")))

        assertThat(refusal).`as`("refusal of an allowed attribute set").isNull()
    }

    @Test
    fun `an empty attribute set passes validation`() {
        assertThat(refusalOf(emptyMap())).`as`("refusal of an empty attribute set").isNull()
    }

    @Test
    fun `more than 32 keys are refused with too-many-keys`() {
        val values = (1..33).associate { "gradle-portal.k$it" to listOf("v") }

        assertThat(refusalOf(values)).`as`("refusal of 33 keys").isEqualTo(AttributeRefusal.TOO_MANY_KEYS)
    }

    @Test
    fun `more than 16 values for one key are refused with too-many-values`() {
        val values = mapOf("gradle-portal.loader" to (1..17).map { "v$it" })

        assertThat(refusalOf(values)).`as`("refusal of 17 values").isEqualTo(AttributeRefusal.TOO_MANY_VALUES)
    }

    @Test
    fun `a value of 2000 characters passes validation`() {
        val values = mapOf("gradle-portal.loader" to listOf("a".repeat(2000)))

        assertThat(refusalOf(values)).`as`("refusal of a value of 2000 characters").isNull()
    }

    @Test
    fun `a value of 2001 characters is refused with value-too-long`() {
        val values = mapOf("gradle-portal.loader" to listOf("a".repeat(2001)))

        assertThat(refusalOf(values)).`as`("refusal of a value of 2001 characters").isEqualTo(AttributeRefusal.VALUE_TOO_LONG)
    }

    @Test
    fun `a set over 8 KiB of key and value text is refused with set-too-large`() {
        val values = (1..5).associate { "gradle-portal.k$it" to listOf("a".repeat(2000)) }

        assertThat(refusalOf(values)).`as`("refusal of 10000 bytes of text").isEqualTo(AttributeRefusal.SET_TOO_LARGE)
    }

    @Test
    fun `a control character in a value is refused with control-character`() {
        val values = mapOf("gradle-portal.loader" to listOf("fab\u0007ric"))

        assertThat(refusalOf(values)).`as`("refusal of a control character").isEqualTo(AttributeRefusal.CONTROL_CHARACTER)
    }

    @Test
    fun `a bidirectional override in a value is refused with control-character`() {
        val values = mapOf("gradle-portal.loader" to listOf("fabric‮"))

        assertThat(refusalOf(values)).`as`("refusal of a bidirectional override").isEqualTo(AttributeRefusal.CONTROL_CHARACTER)
    }

    @Test
    fun `a key of another namespace is refused with foreign-namespace`() {
        val values = mapOf("minecraft-portal.loader" to listOf("fabric"))

        assertThat(refusalOf(values)).`as`("refusal of a foreign namespace").isEqualTo(AttributeRefusal.FOREIGN_NAMESPACE)
    }

    @Test
    fun `a key of the core namespace is refused with foreign-namespace`() {
        val values = mapOf("core.enricher.gradle-portal" to listOf("1"))

        assertThat(refusalOf(values)).`as`("refusal of a core key").isEqualTo(AttributeRefusal.FOREIGN_NAMESPACE)
    }

    @Test
    fun `a key outside the name pattern is refused with invalid-key`() {
        val values = mapOf("gradle-portal.Loader" to listOf("fabric"))

        assertThat(refusalOf(values)).`as`("refusal of an upper case name").isEqualTo(AttributeRefusal.INVALID_KEY)
    }

    @Test
    fun `a key without a namespace is refused with invalid-key`() {
        val values = mapOf("loader" to listOf("fabric"))

        assertThat(refusalOf(values)).`as`("refusal of a key without a dot").isEqualTo(AttributeRefusal.INVALID_KEY)
    }

    @Test
    fun `the refusal reason names the rule and never the value`() {
        val secret = "secret-value‮"

        val reason = AttributeRefusal.CONTROL_CHARACTER.reason

        assertThat(reason).`as`("reason of the refusal").isEqualTo("invalid attributes: control-character")
        assertThat(reason).`as`("reason of the refusal without the value").doesNotContain("secret-value")
        assertThat(refusalOf(mapOf("gradle-portal.loader" to listOf(secret)))).`as`("refusal of the secret value")
            .isEqualTo(AttributeRefusal.CONTROL_CHARACTER)
    }

    @Test
    fun `the attributes result carries the values it was built with`() {
        val values = mapOf("gradle-portal.loader" to listOf("fabric"))

        val result: EnrichmentResult = EnrichmentAttributes(values)

        assertThat(result).`as`("attributes result").isEqualTo(EnrichmentAttributes(values))
    }

}
