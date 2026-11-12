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

package net.onelitefeather.ingot.web.openapi

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class ExtensibleEnumSchemasTest {

    private val mapper = ObjectMapper()

    @Test
    fun `adds x-extensible-enum listing the constant names in declaration order`() {
        val processed = ExtensibleEnumSchemas.apply(document(SAMPLE_SCHEMA), listOf(SampleTrigger::class.java))

        assertThat(processed.at("/components/schemas/SampleTrigger/x-extensible-enum").map { it.asText() })
            .describedAs("x-extensible-enum must list the constants in declaration order, so a new constant is picked up")
            .containsExactly("FIRST", "SECOND", "THIRD")
    }

    @Test
    fun `keeps the closed enum values next to the extension`() {
        val processed = ExtensibleEnumSchemas.apply(document(SAMPLE_SCHEMA), listOf(SampleTrigger::class.java))

        assertThat(processed.at("/components/schemas/SampleTrigger/enum").map { it.asText() })
            .describedAs("the enum array must stay as generated, so the values of existing clients do not change")
            .containsExactly("FIRST", "SECOND", "THIRD")
    }

    @Test
    fun `leaves schemas that are not registered untouched`() {
        val processed = ExtensibleEnumSchemas.apply(document(SAMPLE_SCHEMA, OTHER_SCHEMA), listOf(SampleTrigger::class.java))

        assertThat(processed.at("/components/schemas/Other"))
            .describedAs("a schema that is not an extensible enum must not gain an extension")
            .isEqualTo(mapper.readTree(OTHER_SCHEMA))
    }

    @Test
    fun `fails when a registered enum has no schema in the document`() {
        assertThatThrownBy { ExtensibleEnumSchemas.apply(document(OTHER_SCHEMA), listOf(SampleTrigger::class.java)) }
            .describedAs("a registered enum without a schema must fail instead of being silently skipped")
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("SampleTrigger")
    }

    @Test
    fun `rejects an enum that is not marked as extensible`() {
        assertThatThrownBy { ExtensibleEnumSchemas.apply(document(CLOSED_SCHEMA), listOf(ClosedTrigger::class.java)) }
            .describedAs("only enums annotated with @ExtensibleEnum may be registered")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ClosedTrigger")
    }

    private fun document(vararg schemas: String): ObjectNode =
        mapper.readTree("""{"openapi":"3.1.0","components":{"schemas":{${schemas.joinToString(",")}}}}""") as ObjectNode

    private companion object {
        const val SAMPLE_SCHEMA = """"SampleTrigger":{"type":"string","enum":["FIRST","SECOND","THIRD"]}"""
        const val CLOSED_SCHEMA = """"ClosedTrigger":{"type":"string","enum":["ONE"]}"""
        const val OTHER_SCHEMA = """"Other":{"type":"string","enum":["A","B"]}"""
    }
}

@ExtensibleEnum
internal enum class SampleTrigger { FIRST, SECOND, THIRD }

internal enum class ClosedTrigger { ONE }
