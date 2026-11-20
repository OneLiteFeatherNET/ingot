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

package com.reposilite.javadocs

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.javadocs.application.JavadocSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

internal class JavadocSettingsSchemaTest {

    private val schemaResource = "json-schemes/com.reposilite.javadocs.application.JavadocSettings"

    @Test
    fun `compile-time schema of javadoc settings is readable through the class loader of the settings`() {
        // when: the schema is looked up the way the server looks it up for a plugin's own settings class
        val resource = JavadocSettings::class.java.classLoader.getResource(schemaResource)

        // then: the compile-time schema is on the class path
        assertThat(resource)
            .describedAs("resource $schemaResource must be on the class path, otherwise the javadoc settings fall back to the runtime generator")
            .isNotNull
    }

    @Test
    fun `compile-time schema of javadoc settings names every property of the settings class`() {
        // given: the compile-time schema of the settings class
        val resource = checkNotNull(JavadocSettings::class.java.classLoader.getResource(schemaResource)) {
            "resource $schemaResource must be on the class path"
        }
        val properties = MAPPER.readTree(resource).path("properties")

        // when: the properties of the settings class are collected
        val expected = JavadocSettings::class.memberProperties.map { it.name }

        // then: every property has an entry in the schema, each missing name in its own message
        assertThat(expected)
            .describedAs("properties of JavadocSettings")
            .isNotEmpty
        expected.forEach { name ->
            assertThat(properties.has(name))
                .describedAs("property '$name' of JavadocSettings must be listed under 'properties' in $schemaResource")
                .isTrue
        }
    }

    private companion object {
        val MAPPER: ObjectMapper = ObjectMapper()
    }
}
