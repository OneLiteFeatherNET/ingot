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

package com.reposilite.cleanup

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.cleanup.application.CleanupSettings
import java.lang.reflect.Modifier
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The compile-time settings schema of the cleanup domain is found through the class loader of [CleanupSettings], which is the
 * loader a plugin jar uses once the cleanup code leaves the server. The schema has to name every property of the settings, so a
 * property added to the class without a schema update is reported here and not by the settings page.
 */
internal class CleanupSettingsSchemaTest {

    private val resource = "json-schemes/com.reposilite.cleanup.application.CleanupSettings"

    private fun schemaProperties(): Set<String> {
        val stream = CleanupSettings::class.java.classLoader.getResourceAsStream(resource)
        assertNotNull(stream, "compile-time schema $resource must be readable through the class loader of CleanupSettings")
        val schema = stream!!.use { ObjectMapper().readTree(it) }
        return schema.path("properties").fieldNames().asSequence().toSet()
    }

    @Test
    fun `compile-time schema of CleanupSettings names the policies property`() {
        val properties = schemaProperties()

        assertTrue(
            "policies" in properties,
            "property 'policies' of CleanupSettings is missing from $resource, found: $properties"
        )
    }

    @Test
    fun `compile-time schema of CleanupSettings names every property of the class`() {
        val properties = schemaProperties()
        val declared = CleanupSettings::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }

        declared.forEach { name ->
            assertTrue(
                name in properties,
                "property '$name' of CleanupSettings is missing from $resource, found: $properties"
            )
        }
    }
}
