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

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * The switch and the allowlists of settings secret references, read from an injected environment map.
 */
internal class SettingsSecretReferencesTest {

    @TempDir
    lateinit var directory: Path

    @Test
    fun `the switch is off when nothing is set`() {
        assertThat(SettingsSecretReferences.read(emptyMap())).describedAs("resolver with nothing set").isNull()
    }

    @Test
    fun `the switch is on with the ingot spelling`() {
        val resolver = SettingsSecretReferences.read(mapOf("INGOT_SETTINGS_SECRET_REFERENCES" to "true"))

        assertThat(resolver).describedAs("resolver with the ingot switch").isNotNull()
    }

    @Test
    fun `the switch is on with the legacy spelling when the ingot one is unset`() {
        val resolver = SettingsSecretReferences.read(mapOf("REPOSILITE_SETTINGS_SECRET_REFERENCES" to "true"))

        assertThat(resolver).describedAs("resolver with the legacy switch").isNotNull()
    }

    @Test
    fun `the ingot spelling of the switch takes precedence over the legacy one`() {
        val resolver = SettingsSecretReferences.read(
            mapOf("INGOT_SETTINGS_SECRET_REFERENCES" to "false", "REPOSILITE_SETTINGS_SECRET_REFERENCES" to "true")
        )

        assertThat(resolver).describedAs("resolver when the ingot switch is false").isNull()
    }

    @Test
    fun `an unknown value of the switch is refused`() {
        assertThatThrownBy { SettingsSecretReferences.read(mapOf("INGOT_SETTINGS_SECRET_REFERENCES" to "yes")) }
            .describedAs("switch with an unknown value")
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `the ingot allowlists are comma separated and allow the listed variables`() {
        // given: two variables listed with spaces around the separator
        val resolver = SettingsSecretReferences.read(
            mapOf(
                "INGOT_SETTINGS_SECRET_REFERENCES" to "true",
                "INGOT_SETTINGS_SECRET_ENV" to "S3_SECRET, DB_PASSWORD",
                "S3_SECRET" to "s3cret",
                "DB_PASSWORD" to "dbpass",
            )
        )!!

        // when, then: both listed variables resolve
        assertThat(resolver.resolve("\${env:S3_SECRET}")).describedAs("first listed variable").isEqualTo("s3cret")
        assertThat(resolver.resolve("\${env:DB_PASSWORD}")).describedAs("second listed variable").isEqualTo("dbpass")
    }

    @Test
    fun `a variable that is not listed stays refused under the ingot allowlist`() {
        val resolver = SettingsSecretReferences.read(
            mapOf(
                "INGOT_SETTINGS_SECRET_REFERENCES" to "true",
                "INGOT_SETTINGS_SECRET_ENV" to "S3_SECRET",
                "OTHER" to "other-value",
            )
        )!!

        assertThatThrownBy { resolver.resolve("\${env:OTHER}") }
            .describedAs("variable missing from the ingot allowlist")
            .isInstanceOf(SecretReferenceException::class.java)
    }

    @Test
    fun `the legacy allowlists are used when the ingot ones are unset`() {
        val resolver = SettingsSecretReferences.read(
            mapOf(
                "REPOSILITE_SETTINGS_SECRET_REFERENCES" to "true",
                "REPOSILITE_SETTINGS_SECRET_ENV" to "S3_SECRET",
                "S3_SECRET" to "s3cret",
            )
        )!!

        assertThat(resolver.resolve("\${env:S3_SECRET}")).describedAs("variable of the legacy allowlist").isEqualTo("s3cret")
    }

    @Test
    fun `the file allowlist allows files in the listed directory`() {
        // given: a secret file in a directory listed through the ingot variable
        val secrets = Files.createDirectories(directory.resolve("secrets"))
        val file = Files.writeString(secrets.resolve("s3"), "s3cret")
        val resolver = SettingsSecretReferences.read(
            mapOf(
                "INGOT_SETTINGS_SECRET_REFERENCES" to "true",
                "INGOT_SETTINGS_SECRET_FILES" to secrets.toString(),
            )
        )!!

        // when, then: the file in the listed directory resolves
        assertThat(resolver.resolve("\${file:$file}")).describedAs("file in the listed directory").isEqualTo("s3cret")
    }

    @Test
    fun `a file allowlist entry that is not absolute is refused`() {
        assertThatThrownBy {
            SettingsSecretReferences.read(
                mapOf(
                    "INGOT_SETTINGS_SECRET_REFERENCES" to "true",
                    "INGOT_SETTINGS_SECRET_FILES" to "secrets",
                )
            )
        }
            .describedAs("relative directory in the allowlist")
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
