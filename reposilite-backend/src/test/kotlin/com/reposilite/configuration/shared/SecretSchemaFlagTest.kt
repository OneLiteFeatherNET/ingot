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

package com.reposilite.configuration.shared

import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.reposilite.configuration.shared.api.Doc
import com.reposilite.configuration.shared.api.Sensitive
import com.reposilite.configuration.shared.api.SharedSettings
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * The generated schema flags exactly the secret properties, so the dashboard does not have to guess them from their names.
 */
internal class SecretSchemaFlagTest {

    @Doc(title = "Flagged", description = "Settings with one secret")
    data class FlaggedSettings(
        @get:Sensitive val secretKey: String = "",
        val endpoint: String = "",
    ) : SharedSettings

    @Doc(title = "Plugin flagged", description = "Plugin settings with a secret")
    data class PluginSettings(
        @get:Sensitive val clientSecret: String = "",
        val issuer: String = "",
    ) : SharedSettings

    private fun schemaOf(type: Class<*>) =
        SchemaGenerator(SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_7, SCHEMA_OPTION_PRESET).with(SettingsModule()).build())
            .generateSchema(type)
            .get("properties")

    @Test
    fun `a sensitive property carries the secret flag in the schema`() {
        assertThat(schemaOf(FlaggedSettings::class.java).get("secretKey").get("x-ingot-secret").asBoolean())
            .describedAs("secret flag of secretKey")
            .isTrue()
    }

    @Test
    fun `a property that is not sensitive carries no secret flag`() {
        assertThat(schemaOf(FlaggedSettings::class.java).get("endpoint").has("x-ingot-secret"))
            .describedAs("secret flag of endpoint")
            .isFalse()
    }

    @Test
    fun `a plugin setting marked sensitive carries the same flag`() {
        assertThat(schemaOf(PluginSettings::class.java).get("clientSecret").get("x-ingot-secret").asBoolean())
            .describedAs("secret flag of clientSecret")
            .isTrue()
    }

}
