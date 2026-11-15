/*
 * Copyright (c) 2023 dzikoysk
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

@file:Suppress("FunctionName")

package com.reposilite.console

import com.fasterxml.jackson.databind.ObjectMapper
import com.reposilite.RecommendedLocalSpecificationJunitExtension
import com.reposilite.ReposiliteSpecification
import com.reposilite.audit.AuditFacade
import com.reposilite.audit.AuditQuery
import com.reposilite.audit.AuditRecord
import com.reposilite.audit.api.AuditAction.CONSOLE_COMMAND_EXECUTED
import kong.unirest.core.Unirest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.util.Base64

@ExtendWith(RecommendedLocalSpecificationJunitExtension::class)
internal class ConsoleAuditIntegrationTest : ReposiliteSpecification() {

    private val json = ObjectMapper()

    private fun basic(name: String, secret: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$name:$secret".toByteArray())

    private fun consoleRecords(): List<AuditRecord> =
        useFacade<AuditFacade>().find(AuditQuery(actions = setOf(CONSOLE_COMMAND_EXECUTED))).records

    @Test
    fun `should audit a held dangerous command as a failed console command with exit code 3`() {
        // given: an instance admin and a token that a structured revoke would remove
        val (name, secret) = useDefaultManagementToken()
        useAuth("audit-held", "held-secret")

        // when: the revoke runs in structured mode without confirmation
        Unirest.post("$base/api/console/execute?structured=true")
            .header("Authorization", basic(name, secret))
            .body("token-revoke audit-held")
            .asString()

        // then: one event names the token as actor, the channel, the exit code and the missing confirmation
        val record = consoleRecords().single { it.path == null && it.details.orEmpty().contains("audit-held") }
        assertThat(record.actor).`as`("actor of the held command").isEqualTo(name)
        assertThat(record.success).`as`("success of the held command").isFalse
        val details = json.readTree(record.details!!)
        assertThat(details.get("command").asText()).isEqualTo("token-revoke audit-held")
        assertThat(details.get("channel").asText()).isEqualTo("http")
        assertThat(details.get("confirmed").asBoolean()).isFalse
        assertThat(details.get("exitCode").asInt()).isEqualTo(3)
    }

    @Test
    fun `should audit a confirmed command with the confirmation and exit code 0`() {
        // given: an instance admin and a token that a confirmed revoke removes
        val (name, secret) = useDefaultManagementToken()
        useAuth("audit-confirmed", "confirmed-secret")

        // when: the revoke runs in structured mode with confirmation
        Unirest.post("$base/api/console/execute?structured=true&confirm=true")
            .header("Authorization", basic(name, secret))
            .body("token-revoke audit-confirmed")
            .asString()

        // then: the event records the confirmation and a successful exit code
        val record = consoleRecords().single { it.details.orEmpty().contains("audit-confirmed") }
        assertThat(record.success).isTrue
        val details = json.readTree(record.details!!)
        assertThat(details.get("confirmed").asBoolean()).isTrue
        assertThat(details.get("exitCode").asInt()).isEqualTo(0)
    }

    @Test
    fun `should audit a plain command with its exit code`() {
        // given: an instance admin
        val (name, secret) = useDefaultManagementToken()

        // when: an unknown command runs in plain mode
        Unirest.post("$base/api/console/execute").header("Authorization", basic(name, secret)).body("nosuchaudit").asString()

        // then: the event records the usage exit code and a failure
        val record = consoleRecords().single { it.details.orEmpty().contains("nosuchaudit") }
        assertThat(record.success).isFalse
        assertThat(json.readTree(record.details!!).get("exitCode").asInt()).isEqualTo(2)
    }

    @Test
    fun `should store a sensitive command with its secret redacted`() {
        // given: an instance admin and a token whose secret is regenerated with a known value
        val (name, secret) = useDefaultManagementToken()
        useAuth("audit-secret", "old-secret")

        // when: the token secret is regenerated in structured mode with confirmation
        Unirest.post("$base/api/console/execute?structured=true&confirm=true")
            .header("Authorization", basic(name, secret))
            .body("token-regenerate audit-secret -s s3cr3t-audit-value")
            .asString()

        // then: the event keeps the command name and the redaction marker, and the secret appears nowhere
        val record = consoleRecords().single { it.details.orEmpty().contains("audit-secret") || it.details.orEmpty().contains("token-regenerate") }
        assertThat(json.readTree(record.details!!).get("command").asText()).isEqualTo("token-regenerate <redacted>")
        assertThat(record.toString()).doesNotContain("s3cr3t-audit-value")
    }

}
