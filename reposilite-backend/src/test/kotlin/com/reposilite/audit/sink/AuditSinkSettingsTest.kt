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

package com.reposilite.audit.sink

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class AuditSinkSettingsTest {

    @Test
    fun `the sink is off when no audit sink variable is set`() {
        val settings = AuditSinkSettings.read(emptyMap())

        assertThat(settings.enabled).isFalse
        assertThat(settings.stdout).isFalse
        assertThat(settings.file).isNull()
    }

    @Test
    fun `standard output is switched on only by the value true`() {
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to "true")).stdout).isTrue
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to " true ")).stdout).isTrue
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to "1")).stdout).isFalse
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to "yes")).stdout).isFalse
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to "false")).stdout).isFalse
    }

    @Test
    fun `the legacy standard output variable is read when the ingot name is unset`() {
        assertThat(AuditSinkSettings.read(mapOf("REPOSILITE_AUDIT_SINK_STDOUT" to "true")).stdout).isTrue
    }

    @Test
    fun `the ingot standard output variable wins over the legacy one`() {
        assertThat(AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_STDOUT" to "false", "REPOSILITE_AUDIT_SINK_STDOUT" to "true")).stdout)
            .describedAs("INGOT_ value decides")
            .isFalse
    }

    @Test
    fun `a file path names the file the lines are appended to`() {
        val settings = AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_FILE" to "/data/audit.jsonl"))

        assertThat(settings.file).isEqualTo(Path.of("/data/audit.jsonl"))
        assertThat(settings.enabled).isTrue
    }

    @Test
    fun `a blank file path means no file`() {
        val settings = AuditSinkSettings.read(mapOf("INGOT_AUDIT_SINK_FILE" to "   "))

        assertThat(settings.file).isNull()
        assertThat(settings.enabled).isFalse
    }

    @Test
    fun `the legacy file variable is read when the ingot name is unset`() {
        assertThat(AuditSinkSettings.read(mapOf("REPOSILITE_AUDIT_SINK_FILE" to "/data/audit.jsonl")).file)
            .isEqualTo(Path.of("/data/audit.jsonl"))
    }

    @Test
    fun `the ingot file variable wins over the legacy one`() {
        val settings = AuditSinkSettings.read(
            mapOf("INGOT_AUDIT_SINK_FILE" to "/data/new.jsonl", "REPOSILITE_AUDIT_SINK_FILE" to "/data/old.jsonl")
        )

        assertThat(settings.file).isEqualTo(Path.of("/data/new.jsonl"))
    }

}
