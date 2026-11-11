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

package net.onelitefeather.ingot.nexusimport

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

internal class NexusImportSettingsTest {

    @Test
    fun `should use the documented defaults when no variable is set`() {
        // when: the settings are read from an empty environment
        val settings = NexusImportSettings.read(emptyMap())

        // then: the conservative defaults apply
        assertThat(settings.concurrency).isEqualTo(4)
        assertThat(settings.maxRequestsPerSecond).isEqualTo(5)
        assertThat(settings.audit).isEqualTo(AuditMode.PER_FILE)
        assertThat(settings.allowedHosts).describedAs("unset means any host").isNull()
    }

    @Test
    fun `should prefer the INGOT variable over the REPOSILITE fallback`() {
        // when: both spellings are set for the concurrency
        val settings = NexusImportSettings.read(
            mapOf("INGOT_NEXUS_IMPORT_CONCURRENCY" to "8", "REPOSILITE_NEXUS_IMPORT_CONCURRENCY" to "2")
        )

        // then: the INGOT value wins
        assertThat(settings.concurrency).isEqualTo(8)
    }

    @Test
    fun `should read the REPOSILITE fallback when the INGOT variable is unset`() {
        // when: only the fallback spelling is set
        val settings = NexusImportSettings.read(mapOf("REPOSILITE_NEXUS_IMPORT_CONCURRENCY" to "2"))

        // then: the fallback is used
        assertThat(settings.concurrency).isEqualTo(2)
    }

    @Test
    fun `should refuse a concurrency outside the allowed range and name the variable`() {
        // when / then: a value above the range fails and the message names the variable
        assertThatThrownBy { NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_CONCURRENCY" to "99")) }
            .describedAs("an out-of-range value must fail the start")
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_NEXUS_IMPORT_CONCURRENCY")
    }

    @Test
    fun `should refuse a request rate that is not a number and name the variable`() {
        // when / then: a non-numeric value fails and names the variable
        assertThatThrownBy { NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND" to "fast")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_NEXUS_IMPORT_MAX_REQUESTS_PER_SECOND")
    }

    @Test
    fun `should accept the summary audit mode`() {
        // when: the audit mode is set to summary
        val settings = NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_AUDIT" to "summary"))

        // then: the summary mode is selected
        assertThat(settings.audit).isEqualTo(AuditMode.SUMMARY)
    }

    @Test
    fun `should refuse an unknown audit mode and name the variable`() {
        // when / then: an unknown mode fails the start
        assertThatThrownBy { NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_AUDIT" to "verbose")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("INGOT_NEXUS_IMPORT_AUDIT")
    }

    @Test
    fun `should read the allowed hosts as a trimmed lower case set`() {
        // when: the allow list has spaces and mixed case
        val settings = NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_ALLOWED_HOSTS" to " Nexus.Example.test , backup.example.test"))

        // then: the entries are normalised
        assertThat(settings.allowedHosts).containsExactlyInAnyOrder("nexus.example.test", "backup.example.test")
    }

    @Test
    fun `should have no variable that disables certificate verification`() {
        // when: the settings are read with a variable that sounds like a switch for verification
        val settings = NexusImportSettings.read(mapOf("INGOT_NEXUS_IMPORT_INSECURE_TLS" to "true", "INGOT_NEXUS_IMPORT_VERIFY_TLS" to "false"))

        // then: the settings type has no such field, so the variables change nothing
        assertThat(settings.toString()).describedAs("the settings expose no TLS switch").doesNotContainIgnoringCase("tls")
    }
}
